package su.nightexpress.excellentclaims.hook.impl;

import nl.marido.deluxecombat.hooks.template.BarrierProvider;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.ClaimPlugin;
import su.nightexpress.excellentclaims.api.claim.Claim;
import su.nightexpress.excellentclaims.flag.registry.PlayerFlags;

public class DeluxeCombatHook {
    public static void setup(@NotNull ClaimPlugin plugin) {
        new BarrierProvider() {
            @Override
            public boolean isBorderEnabled() {
                return true;
            }

            @Override
            public String getBorderMode() {
                return "BOTH";
            }

            @Override
            public String getBorderMaterial() {
                return "RED_STAINED_GLASS";
            }

            @Override
            public double getKnockBackStrength() {
                return 0.5;
            }

            @Override
            public boolean allowPvP(Player player, Location location) {
                Claim claim = plugin.getClaimManager().getPrioritizedClaim(location);
                return claim == null || !claim.isInside(location) ||
                        !claim.hasFlag(PlayerFlags.PLAYER_DAMAGE_PLAYERS) ||
                        claim.getFlag(PlayerFlags.PLAYER_DAMAGE_PLAYERS);
            }
        };
    }
}
