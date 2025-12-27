package su.nightexpress.excellentclaims.hook;

import su.nightexpress.nightcore.util.Plugins;

public class Hooks {

    public static final String PROTOCOL_LIB = "ProtocolLib";
    public static final String PACKET_EVENTS = "packetevents";
    public static final String DELUXE_COMBAT = "DeluxeCombat";
    public static final String BETTER_RTP = "BetterRTP";

    public static boolean hasPacketLibrary() {
        return Plugins.isInstalled(PACKET_EVENTS) || Plugins.isInstalled(PROTOCOL_LIB);
    }

    public static boolean hasDeluxeCombat() {
        return Plugins.isInstalled(DELUXE_COMBAT);
    }

    public static boolean hasBetterRtp() {
        return Plugins.isInstalled(BETTER_RTP);
    }
}
