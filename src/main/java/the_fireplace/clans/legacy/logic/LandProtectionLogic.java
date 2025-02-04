package the_fireplace.clans.legacy.logic;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketEntityEquipment;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import the_fireplace.clans.clan.accesscontrol.ClanLocks;
import the_fireplace.clans.clan.accesscontrol.ClanPermissions;
import the_fireplace.clans.clan.admin.AdminControlledClanSettings;
import the_fireplace.clans.clan.membership.ClanMembers;
import the_fireplace.clans.clan.membership.PlayerClans;
import the_fireplace.clans.legacy.ClansModContainer;
import the_fireplace.clans.legacy.api.ClaimAccessor;
import the_fireplace.clans.legacy.cache.RaidingParties;
import the_fireplace.clans.legacy.data.RaidRestoreDatabase;
import the_fireplace.clans.legacy.model.ChunkPositionWithData;
import the_fireplace.clans.legacy.util.BlockSerializeUtil;
import the_fireplace.clans.legacy.util.ChunkUtils;
import the_fireplace.clans.legacy.util.PermissionManager;
import the_fireplace.clans.legacy.util.TextStyles;
import the_fireplace.clans.legacy.util.translation.TranslationUtil;
import the_fireplace.clans.player.ClaimAdmins;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Logic for land protection events goes here.
 */
public class LandProtectionLogic
{
    public static boolean shouldCancelBlockBroken(World world, BlockPos pos, @Nullable EntityPlayerMP breaker) {
        return shouldCancelBlockBroken(world, pos, breaker, true);
    }

    public static boolean shouldCancelBlockBroken(World world, BlockPos pos, @Nullable EntityPlayerMP breaker, boolean showMessage) {
        if (!world.isRemote && ClansModContainer.getConfig().allowBuildProtection()) {
            Chunk c = world.getChunk(pos);
            UUID chunkClan = ChunkUtils.getChunkOwner(c);
            if (chunkClan != null) {
                if (breaker != null) {
                    boolean isRaided = RaidingParties.isRaidedBy(chunkClan, breaker);
                    IBlockState targetState = world.getBlockState(pos);
                    if (ClanLocks.get(chunkClan).isLocked(pos) && !ClanLocks.get(chunkClan).isLockOwner(pos, breaker.getUniqueID()) && !ClanPermissions.get(chunkClan).hasPerm("lockadmin", breaker.getUniqueID())) {
                        if (showMessage)
                        //noinspection ConstantConditions
                        {
                            breaker.sendMessage(TranslationUtil.getTranslation(breaker.getUniqueID(), "clans.protection.break.locked", world.getMinecraftServer().getPlayerProfileCache().getProfileByUUID(ClanLocks.get(chunkClan).getLockOwner(pos)).getName()).setStyle(TextStyles.RED));
                        }
                        return true;
                    }
                    String defaultToPermission = isContainer(world, pos, targetState, null) ? "access" : "build";
                    if (shouldProtectAsNonRaided(breaker, c, chunkClan, isRaided)) {
                        if (showMessage) {
                            breaker.sendMessage(TranslationUtil.getTranslation(breaker.getUniqueID(), ChunkUtils.isBorderland(c) ? "clans.protection.break.borderland" : "clans.protection.break.claimed").setStyle(TextStyles.RED));
                        }
                        return true;
                    } else if (isRaided && !ChunkUtils.isBorderland(c)) {
                        if (targetState.getBlock().hasTileEntity(targetState)) {
                            if (showMessage) {
                                if (ClaimAdmins.isClaimAdmin(breaker)) {
                                    breaker.sendMessage(TranslationUtil.getTranslation(breaker.getUniqueID(), "clans.protection.break.raid").setStyle(TextStyles.RED));
                                } else {
                                    breaker.sendMessage(TranslationUtil.getTranslation(breaker.getUniqueID(), "clans.protection.break.claimed_raid").setStyle(TextStyles.RED));
                                }
                            }
                            return true;
                        }
                    } else if (ClanLocks.get(chunkClan).isLocked(pos) && !ClanLocks.get(chunkClan).hasLockAccess(pos, breaker.getUniqueID(), defaultToPermission)) {
                        if (showMessage)
                        //noinspection ConstantConditions
                        {
                            breaker.sendMessage(TranslationUtil.getTranslation(breaker.getUniqueID(), "clans.protection.break.locked", world.getMinecraftServer().getPlayerProfileCache().getProfileByUUID(ClanLocks.get(chunkClan).getLockOwner(pos)).getName()).setStyle(TextStyles.RED));
                        }
                        return true;
                    }
                }
                return false;
            }
            if (shouldProtectWilderness(world, pos, breaker, "break")) {
                if (breaker != null && showMessage) {
                    breaker.sendMessage(TranslationUtil.getTranslation(breaker.getUniqueID(), "clans.protection.break.wilderness").setStyle(TextStyles.RED));
                }
                return true;
            }
        }
        return false;
    }

    private static boolean shouldProtectWilderness(World world, BlockPos pos, @Nullable EntityPlayerMP breaker, String protectionKey) {
        return ClansModContainer.getConfig().shouldProtectWilderness()
            && isProtectedWilderness(world, pos)
            && !ClansModContainer.getMinecraftHelper().isAllowedNonPlayerEntity(breaker, false)
            && (breaker == null
            || (!ClaimAdmins.isClaimAdmin(breaker)
            && !PermissionManager.hasPermission(breaker, PermissionManager.PROTECTION_PREFIX + protectionKey + ".protected_wilderness", false)));
    }

    private static boolean isProtectedWilderness(World world, BlockPos pos) {
        return ClansModContainer.getConfig().getMinWildernessY() < 0
            ? pos.getY() >= world.getSeaLevel()
            : pos.getY() >= ClansModContainer.getConfig().getMinWildernessY();
    }

    public static boolean shouldCancelCropTrample(World world, BlockPos pos, @Nullable EntityPlayerMP breakingPlayer) {
        if (!world.isRemote && ClansModContainer.getConfig().allowBuildProtection()) {
            Chunk c = world.getChunk(pos);
            UUID chunkClan = ChunkUtils.getChunkOwner(c);
            if (chunkClan != null && breakingPlayer != null) {
                Collection<UUID> playerClans = PlayerClans.getClansPlayerIsIn(breakingPlayer.getUniqueID());
                return (playerClans.isEmpty() || !playerClans.contains(chunkClan))
                    && !RaidingParties.isRaidedBy(chunkClan, breakingPlayer)
                    && !RaidingParties.preparingRaidOnBorderland(breakingPlayer, chunkClan, c);
            }
        }
        return false;
    }

    public static boolean shouldCancelBlockPlacement(World world, BlockPos pos, @Nullable EntityPlayerMP placer, @Nullable EntityEquipmentSlot hand) {
        return shouldCancelBlockPlacement(world, pos, placer, hand, true);
    }

    public static boolean shouldCancelBlockPlacement(World world, BlockPos pos, @Nullable EntityPlayerMP placer, @Nullable EntityEquipmentSlot hand, boolean showMessage) {
        if (!world.isRemote && ClansModContainer.getConfig().allowBuildProtection()) {
            Chunk c = world.getChunk(pos);
            if (placer != null) {
                UUID chunkClan = ChunkUtils.getChunkOwner(c);
                if (chunkClan != null) {
                    if (shouldProtectAsNonRaided(placer, c, chunkClan, RaidingParties.isRaidedBy(chunkClan, placer))) {
                        notifyClientOfCancelledItemUsage(placer, hand);
                        if (showMessage) {
                            placer.sendMessage(TranslationUtil.getTranslation(placer.getUniqueID(), ChunkUtils.isBorderland(c) ? "clans.protection.place.borderland" : "clans.protection.place.territory").setStyle(TextStyles.RED));
                        }
                        return true;
                    }
                    return false;
                }
                if (shouldProtectWilderness(world, pos, placer, "build")) {
                    notifyClientOfCancelledItemUsage(placer, hand);
                    placer.inventory.markDirty();
                    if (showMessage) {
                        placer.sendMessage(TranslationUtil.getTranslation(placer.getUniqueID(), "clans.protection.place.wilderness").setStyle(TextStyles.RED));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldProtectAsNonRaided(EntityPlayer placer, Chunk c, UUID chunkClan, boolean raidedBy) {
        return !ClaimAdmins.isClaimAdmin((EntityPlayerMP) placer)
            && !ClanPermissions.get(chunkClan).hasPerm("build", placer.getUniqueID())
            && !raidedBy
            && !RaidingParties.preparingRaidOnBorderland(placer, chunkClan, c)
            && !ClansModContainer.getMinecraftHelper().isAllowedNonPlayerEntity(placer, false);
    }

    private static void notifyClientOfCancelledItemUsage(EntityPlayerMP player, @Nullable EntityEquipmentSlot hand) {
        if (player.connection != null && hand != null) {
            player.connection.sendPacket(new SPacketEntityEquipment(player.getEntityId(), hand, player.getItemStackFromSlot(hand)));
        }
    }

    public static boolean shouldCancelFluidPlaceBlock(World world, BlockPos sourceLiquidPos, BlockPos fluidPlacingPos) {
        if (!world.isRemote && ClansModContainer.getConfig().allowBuildProtection()) {
            Chunk c = world.getChunk(fluidPlacingPos);
            UUID chunkOwner = ChunkUtils.getChunkOwner(c);
            if (chunkOwner != null) {
                Chunk sourceChunk = world.getChunk(sourceLiquidPos);
                UUID sourceChunkOwner = ChunkUtils.getChunkOwner(sourceChunk);
                return !chunkOwner.equals(sourceChunkOwner);
            }
        }
        return false;
    }

    public static boolean shouldCancelRightClickBlock(World world, BlockPos pos, EntityPlayerMP player, ItemStack heldItem, EnumHand hand) {
        if (!world.isRemote && ClansModContainer.getConfig().allowInteractionProtection()) {
            Chunk c = world.getChunk(pos);
            UUID chunkClan = ChunkUtils.getChunkOwner(c);
            if (chunkClan != null) {
                IBlockState targetState = world.getBlockState(pos);
                TileEntity targetTe = world.getTileEntity(pos);
                boolean isContainer = isContainer(world, pos, targetState, targetTe);
                boolean isRaidedBy = RaidingParties.isRaidedBy(chunkClan, player);
                //Only bypass lock if there is an active raid, stealing is enabled, and the thief is either a raider or a member of the clan (It doesn't make sense to allow raiders to bypass the lock but not the clan members)
                if (ClanLocks.get(chunkClan).isLocked(pos) && (!RaidingParties.hasActiveRaid(chunkClan) || !ClansModContainer.getConfig().isEnableStealing() || !(isRaidedBy || ClanMembers.get(chunkClan).getMemberRanks().containsKey(player.getUniqueID())))) {
                    String defaultToPermission = isContainer ? "access" : "interact";
                    if (!ClanLocks.get(chunkClan).hasLockAccess(pos, player.getUniqueID(), defaultToPermission)) {
                        //noinspection ConstantConditions
                        player.sendMessage(TranslationUtil.getTranslation(player.getUniqueID(), "clans.protection.interact.locked", world.getMinecraftServer().getPlayerProfileCache().getProfileByUUID(ClanLocks.get(chunkClan).getLockOwner(pos)).getName()).setStyle(TextStyles.RED));
                        notifyClientOfCancelledInteract(world, pos, player, targetState, hand);
                        return true;
                    } else {
                        return false;
                    }
                }
                if (!ClaimAdmins.isClaimAdmin(player)
                    && !ClanPermissions.get(chunkClan).hasPerm("interact", player.getUniqueID())
                    && !(isContainer && ClanPermissions.get(chunkClan).hasPerm("access", player.getUniqueID()))
                    && !RaidingParties.preparingRaidOnBorderland(player, chunkClan, c)
                    && (!isRaidedBy
                    || !ClansModContainer.getConfig().isEnableStealing() && isContainer
                    || targetState.getBlock() instanceof BlockDragonEgg)) {
                    if (!(heldItem.getItem() instanceof ItemBlock)) {
                        cancelBlockInteraction(world, pos, player, targetState, hand);
                        return true;
                    } else if (!isRaidedBy
                        || !ClansModContainer.getConfig().isEnableStealing() && isContainer
                        || targetState.getBlock() instanceof BlockDragonEgg) {
                        cancelBlockInteraction(world, pos, player, targetState, hand);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isContainer(World world, BlockPos pos, @Nullable IBlockState state, @Nullable TileEntity tileEntity) {
        if (state == null) {
            state = world.getBlockState(pos);
        }
        if (tileEntity == null) {
            tileEntity = world.getTileEntity(pos);
        }
        return state.getBlock() instanceof BlockContainer || tileEntity instanceof IInventory || ClansModContainer.getProtectionCompat().isContainer(world, pos, state, tileEntity);
    }

    public static boolean shouldCancelEntityInteract(World world, Entity target, EntityPlayer player, boolean useAccessPermission) {
        if (!world.isRemote && ClansModContainer.getConfig().allowInteractionProtection()) {
            Chunk c = world.getChunk(target.getPosition());
            UUID chunkClan = ChunkUtils.getChunkOwner(c);
            if (chunkClan != null && !ChunkUtils.isBorderland(c)) {
                if (player instanceof EntityPlayerMP) {
                    boolean isRaidedBy = RaidingParties.isRaidedBy(chunkClan, player);
                    String permission = useAccessPermission ? "access" : "interact";
                    return !ClaimAdmins.isClaimAdmin((EntityPlayerMP) player)
                        && !ClanPermissions.get(chunkClan).hasPerm(permission, player.getUniqueID())
                        && !RaidingParties.preparingRaidOnBorderland(player, chunkClan, c)
                        && !isRaidedBy;
                }
            }
        }
        return false;
    }

    private static void cancelBlockInteraction(World world, BlockPos pos, EntityPlayerMP interactingPlayer, IBlockState targetState, EnumHand hand) {
        interactingPlayer.sendMessage(TranslationUtil.getTranslation(interactingPlayer.getUniqueID(), "clans.protection.interact.territory").setStyle(TextStyles.RED));
        notifyClientOfCancelledInteract(world, pos, interactingPlayer, targetState, hand);
    }

    private static void notifyClientOfCancelledInteract(World world, BlockPos pos, EntityPlayerMP interactingPlayer, IBlockState targetState, EnumHand hand) {
        //Update the client informing it the interaction did not happen. Go in all directions in case surrounding blocks would have been affected.
        world.notifyBlockUpdate(pos, targetState, targetState, 2);
        world.notifyBlockUpdate(pos.up(), targetState, targetState, 2);
        world.notifyBlockUpdate(pos.down(), targetState, targetState, 2);
        world.notifyBlockUpdate(pos.east(), targetState, targetState, 2);
        world.notifyBlockUpdate(pos.west(), targetState, targetState, 2);
        world.notifyBlockUpdate(pos.north(), targetState, targetState, 2);
        world.notifyBlockUpdate(pos.south(), targetState, targetState, 2);
        notifyClientOfCancelledItemUsage(interactingPlayer, Objects.equals(hand, EnumHand.OFF_HAND) ? EntityEquipmentSlot.OFFHAND : EntityEquipmentSlot.MAINHAND);
    }

    public static void onDetonate(World world, Collection<BlockPos> affectedBlocks, Collection<Entity> affectedEntities) {
        if (!world.isRemote) {
            Set<BlockPos> removeBlocks = Sets.newHashSet();
            for (BlockPos pos : affectedBlocks) {
                Chunk c = world.getChunk(pos);
                UUID chunkClan = ChunkUtils.getChunkOwner(c);
                IBlockState targetState = world.getBlockState(pos);
                if (chunkClan != null) {
                    if (RaidingParties.hasActiveRaid(chunkClan) && !ClansModContainer.getConfig().isDisableRaidRollback() && !ChunkUtils.isBorderland(c) && !targetState.getBlock().hasTileEntity(targetState) && !(targetState.getBlock() instanceof BlockAir) && !(targetState.getBlock() instanceof BlockLiquid)) {
                        RaidRestoreDatabase.addRestoreBlock(c.getWorld().provider.getDimension(), c, pos, BlockSerializeUtil.blockToString(targetState));
                    } else if (!ClansModContainer.getConfig().isChainTNT() || !(targetState.getBlock() instanceof BlockTNT)) {
                        removeBlocks.add(pos);
                    }
                } else if (ClansModContainer.getConfig().shouldProtectWilderness() && (isProtectedWilderness(world, pos)) && (!ClansModContainer.getConfig().isChainTNT() || !(targetState.getBlock() instanceof BlockTNT))) {
                    removeBlocks.add(pos);
                }
            }
            if (ClansModContainer.getConfig().allowBuildProtection()) {
                affectedBlocks.removeAll(removeBlocks);
            }
            if (ClansModContainer.getConfig().allowInjuryProtection()) {
                ArrayList<Entity> removeEntities = Lists.newArrayList();
                for (Entity entity : affectedEntities) {
                    if (entity instanceof EntityPlayer || (isOwnable(entity) && getOwnerId(entity) != null)) {
                        Chunk c = world.getChunk(entity.getPosition());
                        UUID chunkClan = ChunkUtils.getChunkOwner(c);
                        Collection<UUID> entityClans = entity instanceof EntityPlayer ? PlayerClans.getClansPlayerIsIn(entity.getUniqueID()) : PlayerClans.getClansPlayerIsIn(getOwnerId(entity));
                        if (chunkClan != null && !ChunkUtils.isBorderland(c) && !entityClans.isEmpty() && entityClans.contains(chunkClan) && !RaidingParties.hasActiveRaid(chunkClan)) {
                            removeEntities.add(entity);
                        }
                    }
                }
                affectedEntities.removeAll(removeEntities);
            }
        }
    }

    /**
     * @param target   The target of the damage
     * @param source   The damage source should be provided if possible, try to avoid making this null because it can result in invincibility
     * @param attacker The attacker, if there is one
     * @return If the damage should be cancelled
     */
    public static boolean shouldCancelEntityDamage(Entity target, @Nullable DamageSource source, @Nullable Entity attacker) {
        if (target.getEntityWorld().isRemote || !ClansModContainer.getConfig().allowInjuryProtection()) {
            return false;
        }
        Chunk chunk = target.getEntityWorld().getChunk(target.getPosition());
        UUID chunkClan = ChunkUtils.getChunkOwner(chunk);
        if (attacker == null && source != null) {
            attacker = source.getTrueSource();
        }
        //Do not cancel if the attacker is in admin mode or the chunk is not a claim
        boolean attackerIsInAdminMode = attacker instanceof EntityPlayerMP && ClaimAdmins.isClaimAdmin((EntityPlayerMP) attacker);
        if (attackerIsInAdminMode || chunkClan == null || ChunkUtils.isBorderland(chunk)) {
            return false;
        }
        AdminControlledClanSettings adminControlledClanSettings = AdminControlledClanSettings.get(chunkClan);
        //Cancel if mobs cannot do damage and the attacker is a mob
        if (!adminControlledClanSettings.isMobDamageAllowed() && isMob(attacker)) {
            return true;
        }
        //Do not cancel if it would be able to harm creative players or doesn't come from being attacked
        if (source != null && (source.canHarmInCreative() || (attacker == null && !source.isExplosion()))) {
            return false;
        }
        EntityPlayer attackingPlayer = attacker instanceof EntityPlayer ? (EntityPlayer) attacker : isOwnable(attacker) && getOwner(attacker) instanceof EntityPlayer ? (EntityPlayer) getOwner(attacker) : null;
        //Players and their tameables fall into this first category. Including tameables ensures that wolves, Overlord Skeletons, etc are protected
        if (target instanceof EntityPlayer || (isOwnable(target) && getOwnerId(target) != null)) {
            if (!adminControlledClanSettings.hasPVPOverride()) {
                return shouldCancelPVPDefault(target, attacker, chunkClan, attackingPlayer);
            } else //Cancel if pvp is not allowed, don't cancel if pvp is allowed
            {
                return !adminControlledClanSettings.getPVPOverride();
            }
        } else {//Target is not a player and not owned by a player
            if (attackingPlayer != null) {
                UUID attackingPlayerId = attackingPlayer.getUniqueID();
                Collection<UUID> attackerEntityClans = PlayerClans.getClansPlayerIsIn(attackingPlayerId);
                //Players can harm things in their own claims as long as they have permission
                if (attackerEntityClans.contains(chunkClan)) {
                    return !hasPermissionToHarm(target, chunkClan, attackingPlayerId);
                }
                //Raiders can harm things when they are attacking
                if (RaidingParties.isRaidedBy(chunkClan, attackingPlayer)) {
                    return false;
                }
                //Attacker is not a raider and not in the clan.
                //Cancel if the chunk owner has not given permission to harm
                return !hasPermissionToHarm(target, chunkClan, attackingPlayerId)
                    //Allow anyone to kill mobs on server clan land
                    && !(adminControlledClanSettings.isServerOwned() && isMob(target))
                    //Allow fake players to harm things if they are allowed to do so.
                    && !ClansModContainer.getMinecraftHelper().isAllowedNonPlayerEntity(attacker, false);
            }
        }
        return false;
    }

    private static boolean shouldCancelPVPDefault(Entity target, @Nullable Entity attacker, UUID chunkClan, @Nullable EntityPlayer attackingPlayer) {
        UUID targetPlayerId = target instanceof EntityPlayer ? target.getUniqueID() : getOwnerId(target);
        Collection<UUID> targetEntityClans = PlayerClans.getClansPlayerIsIn(targetPlayerId);
        //Cancel if the target player/tameable is in its home territory, not being raided, and not getting hit by their own machines
        if (!targetEntityClans.isEmpty()
            && targetEntityClans.contains(chunkClan)
            && !RaidingParties.hasActiveRaid(chunkClan)
            && targetPlayerId != null
            && !ClansModContainer.getMinecraftHelper().isAllowedNonPlayerEntity(attacker, false)) {
            return true;
        }
        //The attacker is a player (or is owned by a player) and is in a chunk that is being raided and is not an allowed fake player
        else if (RaidingParties.hasActiveRaid(chunkClan)
            && attackingPlayer != null
            && !ClansModContainer.getMinecraftHelper().isAllowedNonPlayerEntity(attacker, false)) {
            //Do not cancel if the attacker is in their home territory.
            if (PlayerClans.getClansPlayerIsIn(attackingPlayer.getUniqueID()).contains(chunkClan)) {
                return false;
            }
            //Cancel if the attacker is not a raider or a raider's tameable
            //Cycle through all the player's clans because we don't want a player to run and hide on a neighboring clan's territory to avoid damage
            for (UUID targetEntityClan : targetEntityClans) {
                if (RaidingParties.isRaidedBy(targetEntityClan, attackingPlayer)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Nullable
    public static UUID getOwnerId(@Nullable Entity entity) {
        if (entity instanceof EntityTameable) {
            return ((EntityTameable) entity).getOwnerId();
        } else if (entity != null) {
            return ClansModContainer.getProtectionCompat().getOwnerId(entity);
        }
        return null;
    }

    @Nullable
    public static Entity getOwner(@Nullable Entity entity) {
        if (entity instanceof EntityTameable) {
            return ((EntityTameable) entity).getOwner();
        } else if (entity != null) {
            try {
                UUID uuid = getOwnerId(entity);
                //Typically the entity will be a player but at least make an attempt to find other entities that may be the owner just in case (mods can do some crazy things)
                return uuid == null ? null : (entity.world instanceof WorldServer ? ((WorldServer) entity.world).getEntityFromUuid(uuid) : entity.world.getPlayerEntityByUUID(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public static boolean isOwnable(@Nullable Entity entity) {
        return entity instanceof EntityTameable || (entity != null && ClansModContainer.getProtectionCompat().isOwnable(entity));
    }

    public static boolean isMob(@Nullable Entity entity) {
        return entity instanceof IMob || (entity != null && ClansModContainer.getProtectionCompat().isMob(entity));
    }

    public static boolean hasPermissionToHarm(Entity targetEntity, UUID permissionClan, UUID attackingPlayerId) {
        if (isMob(targetEntity)) {
            return ClanPermissions.get(permissionClan).hasPerm("harmmob", attackingPlayerId);
        } else if (targetEntity instanceof IAnimals) {
            return ClanPermissions.get(permissionClan).hasPerm("harmanimal", attackingPlayerId);
        } else //Technically other living entities that are not mob or animal may be impacted if for some reason the player does not have build permissions, but what better way is there to handle it considering that item frames, armor stands, and other inanimate entities fall into this category?
        {
            return ClanPermissions.get(permissionClan).hasPerm("build", attackingPlayerId);
        }
    }

    public static boolean shouldCancelEntitySpawn(World world, Entity entity, BlockPos spawnPos) {
        if (!world.isRemote && isMob(entity)) {
            ChunkPositionWithData spawnChunkPosition = new ChunkPositionWithData(world.getChunk(spawnPos)).retrieveCentralData();
            UUID c = ClaimAccessor.getInstance().getChunkClan(spawnChunkPosition);
            if (c != null) {
                AdminControlledClanSettings adminControlledClanSettings = AdminControlledClanSettings.get(c);
                if (adminControlledClanSettings.hasMobSpawningOverride()) {
                    return !adminControlledClanSettings.allowsMobSpawning();
                } else {
                    return claimPreventsMobSpawningByDefault(spawnChunkPosition);
                }
            }
        }
        return false;
    }

    public static boolean claimPreventsMobSpawningByDefault(ChunkPositionWithData claimPosition) {
        return claimPosition.isBorderland()
            ? ClansModContainer.getConfig().isPreventMobsOnBorderlands()
            : ClansModContainer.getConfig().isPreventMobsOnClaims();
    }
}
