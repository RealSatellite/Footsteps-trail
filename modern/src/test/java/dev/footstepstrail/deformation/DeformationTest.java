package dev.footstepstrail.deformation;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class DeformationTest {
    private static BakedQuad top(float height) {
        return new BakedQuad(new Vector3f(0,height,0),new Vector3f(0,height,1),
                new Vector3f(1,height,1),new Vector3f(1,height,0),
                UVPair.pack(0,0),UVPair.pack(0,1),UVPair.pack(1,1),UVPair.pack(1,0),Direction.UP,null);
    }

    private static List<BakedQuad> deform(BlockPos pos, float height, DeformationMaterial material, List<SurfaceImprint> stamps) {
        List<BakedQuad> output = new ArrayList<>();
        QuadInstance light = new QuadInstance();
        light.setColor(0xffabcdef);
        light.setLightCoords(0x00f000f0);
        DeformedQuad.emit((x,y,z,quad,instance) -> {
            for(int i=0;i<4;i++) {
                assertEquals("Opaque terrain must stay opaque",255,instance.getColor(i)>>>24);
                assertEquals(0x00f000f0,instance.getLightCoords(i));
            }
            output.add(quad);
        },0,0,0,top(height),light,pos,material,stamps);
        return output;
    }

    @Test public void sandHasRealCavityRaisedRimAndContinuousTextureCoordinates() {
        List<BakedQuad> quads=deform(BlockPos.ZERO,1,DeformationMaterial.SAND,
                List.of(new SurfaceImprint(0.5,1,0.5,0,0.2,1,0)));
        float low=1,high=1;
        for(BakedQuad q:quads) for(int i=0;i<4;i++) {
            low=Math.min(low,q.position(i).y()); high=Math.max(high,q.position(i).y());
            assertEquals(q.position(i).x(),UVPair.unpackU(q.packedUV(i)),0.000001);
            assertEquals(q.position(i).z(),UVPair.unpackV(q.packedUV(i)),0.000001);
        }
        assertTrue("Sole must actually be below the block top",low<0.985);
        assertTrue("Sand must be displaced above the block top",high>1.002);
        assertTrue("Adaptive subdivision must remain bounded",quads.size()<=1024);
    }

    @Test public void sharedBlockEdgeIsWatertight() {
        List<SurfaceImprint> stamps=List.of(new SurfaceImprint(1,1,0.5,35,0.3,1,0));
        var left=deform(BlockPos.ZERO,1,DeformationMaterial.SAND,stamps);
        var right=deform(new BlockPos(1,0,0),1,DeformationMaterial.SAND,stamps);
        java.util.Map<Float,Float> heights=new java.util.HashMap<>();
        for(var q:left) for(int i=0;i<4;i++) if(q.position(i).x()==1) heights.put(q.position(i).z(),q.position(i).y());
        for(var q:right) for(int i=0;i<4;i++) if(q.position(i).x()==0) {
            assertNotNull(heights.get(q.position(i).z()));
            assertEquals(heights.get(q.position(i).z()),q.position(i).y(),0.000001);
        }
    }

    @Test public void repeatedStepsCannotPunctureThinSnow() {
        List<SurfaceImprint> stamps=new ArrayList<>();
        for(int i=0;i<24;i++) stamps.add(new SurfaceImprint(0.5,0.0625,0.5,0,0.2,2,i));
        for(var q:deform(BlockPos.ZERO,0.0625f,DeformationMaterial.SNOW,stamps))
            for(int i=0;i<4;i++) assertTrue(q.position(i).y()>0.02);
    }

    @Test public void hardSurfacesAreShallowerAndStickyMaterialsRecoverDifferently() {
        SurfaceImprint stamp=new SurfaceImprint(0,1,0,0,0.2,1,0);
        assertTrue(Math.abs(stamp.height(0,0,DeformationMaterial.STONE))
                < Math.abs(stamp.height(0,0,DeformationMaterial.SAND))/3);
        assertEquals(0,stamp.height(2,2,DeformationMaterial.SAND),0);
    }

    @Test public void zeroStrengthRestoresSurfaceExactlyAndDoesNotMutateSource() {
        BakedQuad original=top(1);
        QuadInstance light = new QuadInstance();
        light.setColor(0xffffffff);
        DeformedQuad.emit((x,y,z,q,l) -> {
            for(int i=0;i<4;i++) assertEquals(1,q.position(i).y(),0);
        },0,0,0,original,light,BlockPos.ZERO,DeformationMaterial.SAND,
                List.of(new SurfaceImprint(0.5,1,0.5,0,0.2,0,0)));
        for(int i=0;i<4;i++) assertEquals(1,original.position(i).y(),0);
    }

    @Test public void agingKeepsFootOutlineAndHandednessStable() {
        SurfaceImprint original = new SurfaceImprint(2,1,3,73,0.2,1,7);
        SurfaceImprint aged = original.withStrength(0.5);
        for(double x=1.7;x<2.3;x+=0.01) for(double z=2.7;z<3.3;z+=0.01)
            assertEquals(original.height(x,z,DeformationMaterial.SAND)*0.5,
                    aged.height(x,z,DeformationMaterial.SAND),0.0000001);
        assertFalse(original.sameGeometry(new SurfaceImprint(2,1,3,90,0.2,1,7)));
    }

    @Test public void squareBootSoleKeepsWaistWidthWithoutArchNarrowing() {
        SurfaceImprint stamp = new SurfaceImprint(0, 1, 0, 0, 0.2, 1, 0);
        double center = stamp.height(0, 0, DeformationMaterial.SAND);
        assertTrue("Sole center must be recessed", center < 0);
        // One boot half-width out along the side axis: a square Minecraft sole
        // stays deep here, while an arch-narrowed ellipse pinches shut.
        double waist = stamp.height(0.06, 0, DeformationMaterial.SAND);
        assertTrue("Square sole must stay recessed at the waist", waist < 0);
        assertTrue("Waist must keep most of the center depth, got " + (waist / center),
                waist / center > 0.72);
        // The sole edge must still hold depth: a boot-scale square print, not a small dot.
        double edge = stamp.height(0.09, 0, DeformationMaterial.SAND);
        assertTrue("Square sole must reach the boot edge, got " + (edge / center),
                edge / center > 0.35);
    }

    @Test public void softGroundStaysReadableAgainstSand() {
        SurfaceImprint stamp = new SurfaceImprint(0, 1, 0, 0, 0.2, 1, 0);
        double sand = stamp.height(0, 0, DeformationMaterial.SAND);
        for (DeformationMaterial material : List.of(DeformationMaterial.ORGANIC,
                DeformationMaterial.EARTH, DeformationMaterial.GRAVEL)) {
            double dent = stamp.height(0, 0, material);
            assertTrue(material + " dents must stay deep enough to catch light, got " + (dent / sand),
                    dent / sand > 0.75);
        }
    }

    @Test public void materialEdgeBlendingHasNoGapOrLossOfWeight() {
        for(double x=-0.1;x<2.1;x+=0.005) {
            double sum=0;
            for(int block=-1;block<=2;block++) sum+=SurfaceHeights.weight(x,block);
            assertEquals(1,sum,0.0000001);
        }
        assertEquals(0.5,SurfaceHeights.weight(1,0),0.0000001);
        assertEquals(0.5,SurfaceHeights.weight(1,1),0.0000001);
    }

    @Test public void hardSurfaceSolesHaveReadableReliefAndContrastWithoutChangingOpacity() {
        List<SurfaceImprint> stamps = List.of(new SurfaceImprint(0.5,1,0.5,0,0.2,1,0));
        double sandFloor = SurfaceHeights.sample(0.5,1,0.5,0,DeformationMaterial.SAND,stamps);
        for (DeformationMaterial material : List.of(DeformationMaterial.STONE,
                DeformationMaterial.WOOD,DeformationMaterial.METAL,DeformationMaterial.GLASS)) {
            QuadInstance light = new QuadInstance();
            light.setColor(0xffffffff);
            light.setLightCoords(0x00f000f0);
            int[] centerVertices = {0};
            DeformedQuad.emit((x,y,z,quad,instance) -> {
                for (int i=0;i<4;i++) {
                    assertEquals(255,instance.getColor(i)>>>24);
                    if (quad.position(i).x()==0.5f && quad.position(i).z()==0.5f) {
                        centerVertices[0]++;
                        double relief = 1-quad.position(i).y();
                        assertTrue(material+" needs measurable recessed geometry",relief>0.0025);
                        assertTrue(material+" must remain shallower than sand",relief<-sandFloor/2);
                        int red = (instance.getColor(i)>>>16)&255;
                        assertTrue(material+" needs visible cavity contrast on its actual terrain vertices",red<=225);
                        assertTrue(material+" must retain the underlying block texture",red>=180);
                    }
                }
            },0,0,0,top(1),light,BlockPos.ZERO,material,stamps);
            assertTrue("The emitted mesh must include the sole center",centerVertices[0]>0);
        }
    }
}
