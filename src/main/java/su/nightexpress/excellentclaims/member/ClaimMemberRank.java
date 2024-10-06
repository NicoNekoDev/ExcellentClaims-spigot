package su.nightexpress.excellentclaims.member;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.Placeholders;
import su.nightexpress.excellentclaims.api.claim.ClaimPermission;
import su.nightexpress.excellentclaims.api.member.MemberRank;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.StringUtil;
import su.nightexpress.nightcore.util.placeholder.PlaceholderMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ClaimMemberRank implements MemberRank {

    private final String               id;
    private final String               displayName;
    private final int                  priority;
    private final Set<ClaimPermission> permissions;

    private final PlaceholderMap placeholders;

    public ClaimMemberRank(@NotNull String id, @NotNull String displayName, int priority, @NotNull Set<ClaimPermission> permissions) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.priority = priority;
        this.permissions = permissions;

        this.placeholders = Placeholders.forMemberRank(this);
    }

    @NotNull
    public static List<ClaimMemberRank> getDefaultRanks() {
        List<ClaimMemberRank> list = new ArrayList<>();

        list.add(new ClaimMemberRank("member", "Member", 1, Lists.newSet(
            ClaimPermission.BUILDING, ClaimPermission.BLOCK_INTERACT, ClaimPermission.VIEW_MEMBERS, ClaimPermission.TELEPORT))
        );

        list.add(new ClaimMemberRank("trusted", "Trusted", 5, Lists.newSet(
            ClaimPermission.BUILDING, ClaimPermission.CONTAINERS, ClaimPermission.VIEW_MEMBERS, ClaimPermission.TELEPORT,
            ClaimPermission.BLOCK_INTERACT, ClaimPermission.ENTITY_INTERACT))
        );

        list.add(new ClaimMemberRank("officer", "Officer", 10, Lists.newSet(
            ClaimPermission.BUILDING, ClaimPermission.CONTAINERS, ClaimPermission.VIEW_MEMBERS, ClaimPermission.TELEPORT,
            ClaimPermission.BLOCK_INTERACT, ClaimPermission.ENTITY_INTERACT,
            ClaimPermission.ADD_MEMBERS, ClaimPermission.REMOVE_MEMBERS, ClaimPermission.MANAGE_MEMBERS))
        );

        list.add(new ClaimMemberRank("owner", "Owner", 100, Lists.newSet(
            ClaimPermission.ALL))
        );

        return list;
    }

    @NotNull
    public static List<ClaimMemberRank> getDummyRanks() {
        List<ClaimMemberRank> list = new ArrayList<>();

        list.add(new ClaimMemberRank("member", "Member", 1, Lists.newSet(
            ClaimPermission.BUILDING, ClaimPermission.BLOCK_INTERACT, ClaimPermission.TELEPORT))
        );

        list.add(new ClaimMemberRank("owner", "Owner", 100, Lists.newSet(
            ClaimPermission.ALL))
        );

        return list;
    }

    @NotNull
    public static ClaimMemberRank read(@NotNull FileConfig config, @NotNull String path, @NotNull String id) {
        String displayName = config.getString(path + ".DisplayName", StringUtil.capitalizeUnderscored(id));
        int priority = config.getInt(path + ".Priority");

        Set<ClaimPermission> permissions = Lists.modify(config.getStringSet(path + ".Permissions"),
            name -> StringUtil.getEnum(name, ClaimPermission.class).orElse(null));
        permissions.removeIf(Objects::isNull);

        return new ClaimMemberRank(id, displayName, priority, permissions);
    }

    public void write(@NotNull FileConfig config, @NotNull String path) {
        config.set(path + ".DisplayName", this.displayName);
        config.set(path + ".Priority", this.priority);
        config.set(path + ".Permissions", Lists.modify(this.permissions, Enum::name));
    }

    @NotNull
    @Override
    public PlaceholderMap getPlaceholders() {
        return this.placeholders;
    }

    @Override
    public boolean hasPermission(@NotNull ClaimPermission permission) {
        return this.permissions.contains(ClaimPermission.ALL) || this.permissions.contains(permission);
    }

    @Override
    public boolean isAbove(@NotNull MemberRank other) {
        return this.getPriority() > other.getPriority();
    }

    @Override
    public boolean isBehind(@NotNull MemberRank other) {
        return this.getPriority() < other.getPriority();
    }

    @NotNull
    @Override
    public String getId() {
        return id;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Set<ClaimPermission> getPermissions() {
        return permissions;
    }
}
