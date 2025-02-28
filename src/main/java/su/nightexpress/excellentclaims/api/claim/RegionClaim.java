package su.nightexpress.excellentclaims.api.claim;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentclaims.util.pos.BlockPos;
import su.nightexpress.excellentclaims.util.Cuboid;

public interface RegionClaim extends Claim {

    @NotNull Cuboid getCuboid();

    @Nullable BlockPos getBlockPos();

    void setCuboid(@NotNull BlockPos min, @NotNull BlockPos max);

    void setCuboid(@NotNull Cuboid cuboid);

    void setBlockPos(@NotNull BlockPos pos);
}
