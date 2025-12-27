package su.nightexpress.excellentclaims.hook.impl;

import me.SuperRonanCraft.BetterRTP.references.customEvents.RTP_FindLocationEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.ClaimPlugin;
import su.nightexpress.excellentclaims.api.claim.Claim;

public class BetterRtpHook implements Listener {
    private final ClaimPlugin plugin;

    public BetterRtpHook(@NotNull ClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public static void setup(@NotNull ClaimPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new BetterRtpHook(plugin), plugin);
    }

    @EventHandler
    public void onLocationFind(RTP_FindLocationEvent event) {
        Location location = event.getLocation();
        if (location == null) return;
        Claim claim = plugin.getClaimManager().getPrioritizedClaim(location);
        if (claim == null) return;
        if (!claim.isInside(location)) return;
        event.setCancelled(true);
    }
}
