package dev.footstepstrail.command;

/**
 * The list shown by /footsteps help. Shared by every loader so the help
 * text stays the same everywhere.
 */
public final class FootstepsHelp {

    /** Each entry is {usage after "/footsteps", description}. */
    public static final String[][] ENTRIES = new String[][] {
            { "status", "Show current status (same as plain /footsteps)" },
            { "help", "Show this list of commands" },
            { "toggle", "Turn the trail on or off" },
            { "clear", "Remove all current footprints" },
            { "type <terrain|surface|classic>", "Choose the footprint style" },
            { "mode <combined|time|steps>", "combined (default): a footprint expires at whichever limit comes first, time or steps. time: time limit only. steps: step limit only" },
            { "size <0.05-2.0>", "Set the footprint size" },
            { "config time set <1-300>", "How many seconds footprints last (combined and time modes)" },
            { "config steps set <1-500>", "How many footprints to keep (combined and steps modes)" },
            { "config rotation <true|false>", "Turn footprints to face your walking direction" },
            { "config alternating <true|false>", "Alternate left and right feet" },
            { "config stealth <true|false>", "Leave no footprints while sneaking" },
            { "config scuffs <true|false>", "Scuff marks on hard surfaces" },
            { "appearance shape <square|circle|paw|cross>", "Marker shape (classic only)" },
            { "appearance color <white|gray|red|green|blue|yellow>", "Marker color (classic only)" },
            { "appearance glow [true|false]", "Toggle the glow (classic only)" },
    };

    private FootstepsHelp() {
    }

    public static String command(String[] entry) {
        return "/footsteps " + entry[0];
    }

    public static String description(String[] entry) {
        return " - " + entry[1];
    }
}
