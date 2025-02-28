package su.nightexpress.excellentclaims;

import nl.marido.deluxecombat.hooks.template.BarrierProvider;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.api.claim.Claim;
import su.nightexpress.excellentclaims.api.claim.RegionClaim;
import su.nightexpress.excellentclaims.api.flag.ClaimFlag;
import su.nightexpress.excellentclaims.claim.ClaimManager;
import su.nightexpress.excellentclaims.command.impl.BaseCommands;
import su.nightexpress.excellentclaims.command.impl.LandCommands;
import su.nightexpress.excellentclaims.command.impl.RegionCommands;
import su.nightexpress.excellentclaims.command.impl.WildernessCommands;
import su.nightexpress.excellentclaims.config.Config;
import su.nightexpress.excellentclaims.config.Keys;
import su.nightexpress.excellentclaims.config.Lang;
import su.nightexpress.excellentclaims.config.Perms;
import su.nightexpress.excellentclaims.data.storage.DataManager;
import su.nightexpress.excellentclaims.data.user.UserManager;
import su.nightexpress.excellentclaims.flag.FlagRegistry;
import su.nightexpress.excellentclaims.flag.list.PlayerFlags;
import su.nightexpress.excellentclaims.hook.Hooks;
import su.nightexpress.excellentclaims.member.MemberManager;
import su.nightexpress.excellentclaims.menu.MenuManager;
import su.nightexpress.excellentclaims.selection.SelectionManager;
import su.nightexpress.nightcore.NightPlugin;
import su.nightexpress.nightcore.command.experimental.ImprovedCommands;
import su.nightexpress.nightcore.config.PluginDetails;

import java.util.Set;

public class ClaimPlugin extends NightPlugin implements ImprovedCommands {

    private DataManager dataManager;
    private UserManager userManager;

    private MemberManager memberManager;
    private ClaimManager claimManager;
    private SelectionManager selectionManager;
    private MenuManager menuManager;

    @Override
    @NotNull
    protected PluginDetails getDefaultDetails() {
        return PluginDetails.create("Claims", new String[]{"eclaim", "eclaims", "excellentclaims"})
                .setConfigClass(Config.class)
                .setLangClass(Lang.class)
                .setPermissionsClass(Perms.class);
    }

    @Override
    public void enable() {
        this.loadAPI();
        this.loadFlags();
        this.loadCommands();

        this.dataManager = new DataManager(this);
        this.dataManager.setup();

        this.userManager = new UserManager(this, this.dataManager);
        this.userManager.setup();

        this.memberManager = new MemberManager(this);
        this.memberManager.setup();

        this.claimManager = new ClaimManager(this);
        this.claimManager.setup();

        this.menuManager = new MenuManager(this);
        this.menuManager.setup();

        this.selectionManager = new SelectionManager(this);
        this.selectionManager.setup();

        if (Hooks.hasDeluxeCombat())
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
                public boolean allowPvP(Player p, Location location) {
                    Set<RegionClaim> claims = claimManager.getRegionClaims(location);
                    if (!claims.isEmpty())
                        for (RegionClaim claim : claims)
                            if (claim.getCuboid().contains(location) &&
                                    claim.hasFlag(PlayerFlags.PLAYER_DAMAGE_PLAYERS) &&
                                    !claim.getFlag(PlayerFlags.PLAYER_DAMAGE_PLAYERS))
                                return false;
                    return true;
                }
            };
    }

    @Override
    public void disable() {
        if (this.menuManager != null) this.menuManager.shutdown();
        if (this.selectionManager != null) this.selectionManager.shutdown();
        if (this.claimManager != null) this.claimManager.shutdown();
        if (this.memberManager != null) this.memberManager.shutdown();

        this.unloadCommands();

        FlagRegistry.shutdown();
        ClaimsAPI.shutdown();
        Keys.shutdown();
    }

    private void loadAPI() {
        Keys.load(this);
        ClaimsAPI.load(this);
    }

    private void loadFlags() {
        FlagRegistry.load(this);
    }

    private void loadCommands() {
        BaseCommands.load(this);
        WildernessCommands.load(this);
        RegionCommands.load(this);
        LandCommands.load(this);
    }

    private void unloadCommands() {
        RegionCommands.unload();
        LandCommands.unload();
        WildernessCommands.unload();
    }

    @NotNull
    public DataManager getDataManager() {
        return this.dataManager;
    }

    @NotNull
    public UserManager getUserManager() {
        return this.userManager;
    }

    @NotNull
    public MenuManager getMenuManager() {
        return this.menuManager;
    }

    @NotNull
    public MemberManager getMemberManager() {
        return this.memberManager;
    }

    @NotNull
    public ClaimManager getClaimManager() {
        return this.claimManager;
    }

    @NotNull
    public SelectionManager getSelectionManager() {
        return this.selectionManager;
    }
}