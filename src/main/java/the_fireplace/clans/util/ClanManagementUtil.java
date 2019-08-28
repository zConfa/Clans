package the_fireplace.clans.util;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.apache.commons.lang3.ArrayUtils;
import the_fireplace.clans.Clans;
import the_fireplace.clans.api.event.PreLandAbandonEvent;
import the_fireplace.clans.api.event.PreLandClaimEvent;
import the_fireplace.clans.cache.ClanCache;
import the_fireplace.clans.cache.PlayerCache;
import the_fireplace.clans.data.ClaimData;
import the_fireplace.clans.data.PlayerData;
import the_fireplace.clans.model.ChunkPosition;
import the_fireplace.clans.model.ChunkPositionWithData;
import the_fireplace.clans.model.Clan;
import the_fireplace.clans.model.EnumRank;
import the_fireplace.clans.util.translation.TranslationUtil;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public class ClanManagementUtil {
    public static boolean checkAndAttemptClaim(EntityPlayerMP sender, Clan selectedClan, boolean force) {
        return checkAndAttemptClaim(sender, selectedClan, new ChunkPositionWithData(sender.chunkCoordX, sender.chunkCoordZ, sender.getEntityWorld().provider.getDimension()).retrieveCentralData(), force);
    }

    public static boolean checkAndAttemptClaim(EntityPlayerMP sender, Clan selectedClan, ChunkPositionWithData claimChunk, boolean force) {
        UUID claimOwner = ClaimData.getChunkClanId(claimChunk);
        Clan claimClan = ClanCache.getClanById(claimOwner);
        if(claimOwner != null && claimClan != null && (!force || claimOwner.equals(selectedClan.getClanId()))) {
            if(!claimOwner.equals(selectedClan.getClanId())) {
                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.taken_other", claimClan.getClanName()).setStyle(TextStyles.RED));
                return false;
            } else if(!claimChunk.isBorderland()) {
                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.taken", selectedClan.getClanName()).setStyle(TextStyles.YELLOW));
                return false;
            }
        }
        if(claimClan != null && !claimChunk.isBorderland() && !claimClan.isLimitless()) {//In this scenario, we are always forcing the claim, so we should refund the clan the land is being taken from
            claimClan.refundClaim();
        }
        if(selectedClan.isLimitless()) {
            ClaimData.swapChunk(claimChunk, claimOwner, selectedClan.getClanId());
            sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.success", selectedClan.getClanName()).setStyle(TextStyles.GREEN));
            return true;
        } else {
            if (force || !Clans.getConfig().isForceConnectedClaims() || ChunkUtils.hasConnectedClaim(claimChunk, selectedClan.getClanId()) || selectedClan.getClaimCount() == 0) {
                if (force || Clans.getConfig().getMaxClaims() <= 0 || selectedClan.getClaimCount() < selectedClan.getMaxClaimCount()) {
                    if (selectedClan.getClaimCount() > 0)
                        claimChunk(sender, claimChunk, selectedClan, force);
                    else if (Clans.getConfig().getMinClanHomeDist() > 0 && Clans.getConfig().getInitialClaimSeparationMultiplier() > 0) {
                        boolean inClanHomeRange = false;
                        for (Map.Entry<Clan, BlockPos> pos : ClanCache.getClanHomes().entrySet())
                            if (!pos.getKey().getClanId().equals(selectedClan.getClanId()) && pos.getKey().hasHome() && pos.getValue() != null && pos.getValue().getDistance(sender.getPosition().getX(), sender.getPosition().getY(), sender.getPosition().getZ()) < Clans.getConfig().getMinClanHomeDist() * Clans.getConfig().getInitialClaimSeparationMultiplier())
                                inClanHomeRange = true;
                        if (inClanHomeRange) {
                            if (Clans.getConfig().isEnforceInitialClaimSeparation())
                                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.proximity_error", Clans.getConfig().getMinClanHomeDist() * Clans.getConfig().getInitialClaimSeparationMultiplier()).setStyle(TextStyles.RED));
                            else if (PlayerCache.getClaimWarning(sender.getUniqueID()))
                                return claimChunk(sender, claimChunk, selectedClan, force);
                            else {
                                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.proximity_warning", Clans.getConfig().getMinClanHomeDist() * Clans.getConfig().getInitialClaimSeparationMultiplier()).setStyle(TextStyles.YELLOW));
                                PlayerCache.setClaimWarning(sender.getUniqueID(), true);
                            }
                        } else
                            return claimChunk(sender, claimChunk, selectedClan, force);
                    } else
                        return claimChunk(sender, claimChunk, selectedClan, force);
                } else
                    sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.maxed", selectedClan.getClanName(), selectedClan.getMaxClaimCount()).setStyle(TextStyles.RED));
            } else
                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.disconnected", selectedClan.getClanName()).setStyle(TextStyles.RED));
        }
        return false;
    }

    public static boolean claimChunk(EntityPlayerMP sender, ChunkPositionWithData claimChunk, Clan selectedClan, boolean force) {
        if (force || selectedClan.payForClaim()) {
            PreLandClaimEvent event = ClansEventManager.fireEvent(new PreLandClaimEvent(sender.world, sender.world.getChunk(claimChunk.getPosX(), claimChunk.getPosZ()), claimChunk, sender.getUniqueID(), selectedClan));
            if(!event.isCancelled) {
                ClaimData.swapChunk(claimChunk, null, selectedClan.getClanId());
                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.success", selectedClan.getClanName()).setStyle(TextStyles.GREEN));
                return true;
            } else {
                sender.sendMessage(event.cancelledMessage);
                return false;
            }
        } else
            sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.claim.insufficient_funds", selectedClan.getClanName(), Clans.getConfig().getClaimChunkCost(), Clans.getPaymentHandler().getCurrencyName(Clans.getConfig().getClaimChunkCost())).setStyle(TextStyles.RED));
        return false;
    }

    public static boolean checkAndAttemptAbandon(EntityPlayerMP sender, @Nullable Clan selectedClan) {
        return checkAndAttemptAbandon(sender, selectedClan, new ChunkPositionWithData(sender.chunkCoordX, sender.chunkCoordZ, sender.getEntityWorld().provider.getDimension()).retrieveCentralData());
    }

    public static boolean checkAndAttemptAbandon(EntityPlayerMP sender, @Nullable Clan selectedClan, ChunkPositionWithData claimChunk) {
        Chunk c = sender.getEntityWorld().getChunk(sender.getPosition());
        UUID claimOwnerClanId = ChunkUtils.getChunkOwner(c);
        if(claimOwnerClanId != null && !claimChunk.isBorderland()) {
            Clan claimOwnerClan = ClanCache.getClanById(claimOwnerClanId);
            if(claimOwnerClan == null) {
                ChunkUtils.clearChunkOwner(c);
                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.abandonclaim.success", "null").setStyle(TextStyles.GREEN));
                return true;
            }
            if(selectedClan == null || claimOwnerClanId.equals(selectedClan.getClanId())) {
                if(selectedClan == null || claimOwnerClan.isLimitless() || !Clans.getConfig().isForceConnectedClaims() || ChunkUtils.canBeAbandoned(c, claimOwnerClanId)) {
                    return finishClaimAbandonment(sender, c, claimOwnerClan);
                } else {//We are forcing connected claims and there is a claim connected
                    //Prevent creation of disconnected claims
                    return abandonClaimWithAdjacencyCheck(sender, c, claimOwnerClan);
                }
            } else
                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.abandonclaim.wrongclan", selectedClan.getClanName()).setStyle(TextStyles.RED));
        } else
            sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.abandonclaim.notclaimed").setStyle(TextStyles.RED));
        return false;
    }

    public static void abandonClaim(EntityPlayerMP sender, Chunk c, Clan targetClan) {
        //Unset clan home if it is in the chunk
        if (targetClan.getHome() != null
                && targetClan.hasHome()
                && sender.dimension == targetClan.getHomeDim()
                && targetClan.getHome().getX() >= c.getPos().getXStart()
                && targetClan.getHome().getX() <= c.getPos().getXEnd()
                && targetClan.getHome().getZ() >= c.getPos().getZStart()
                && targetClan.getHome().getZ() <= c.getPos().getZEnd()) {
            targetClan.unsetHome();
        }

        ClaimData.delChunk(targetClan, new ChunkPositionWithData(c));
        if(!targetClan.isLimitless())
            Clans.getPaymentHandler().addAmount(Clans.getConfig().getClaimChunkCost(), targetClan.getClanId());
    }

    public static boolean abandonClaimWithAdjacencyCheck(EntityPlayerMP sender, Chunk c, Clan targetClan) {
        boolean allowed = true;
        for (Chunk checkChunk : ChunkUtils.getConnectedClaimChunks(c, targetClan.getClanId())) {
            if (ChunkUtils.getConnectedClaimChunks(checkChunk, targetClan.getClanId()).equals(Lists.newArrayList(c))) {
                allowed = false;
                break;
            }
        }
        if (allowed) {
            return finishClaimAbandonment(sender, c, targetClan);
        } else
            sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.opclan.abandonclaim.disconnected").setStyle(TextStyles.RED));
        return false;
    }

    public static boolean finishClaimAbandonment(EntityPlayerMP sender, Chunk c, Clan targetClan) {
        PreLandAbandonEvent event = ClansEventManager.fireEvent(new PreLandAbandonEvent(sender.world, c, new ChunkPosition(c), sender.getUniqueID(), targetClan));
        if(!event.isCancelled) {
            abandonClaim(sender, c, targetClan);
            sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.abandonclaim.success", targetClan.getClanName()).setStyle(TextStyles.GREEN));
            return true;
        } else
            sender.sendMessage(event.cancelledMessage);
        return false;
    }

    public static void promoteClanMember(MinecraftServer server, ICommandSender sender, String playerName, Clan clan) throws CommandException {
        GameProfile target = server.getPlayerProfileCache().getGameProfileForUsername(playerName);

        if(target != null) {
            if (!ClanCache.getPlayerClans(target.getId()).isEmpty()) {
                if (ClanCache.getPlayerClans(target.getId()).contains(clan)) {
                    if (clan.promoteMember(target.getId())) {
                        sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.promote.success", target.getName(), clan.getMembers().get(target.getId()).toString().toLowerCase(), clan.getClanName()).setStyle(TextStyles.GREEN));
                        for(Map.Entry<EntityPlayerMP, EnumRank> m : clan.getOnlineMembers().entrySet())
                            if(m.getValue().greaterOrEquals(clan.getMembers().get(target.getId())))
                                if(!m.getKey().getUniqueID().equals(target.getId()))
                                    m.getKey().sendMessage(TranslationUtil.getTranslation(m.getKey().getUniqueID(), "commands.clan.promote.notify", target.getName(), clan.getMembers().get(target.getId()).toString().toLowerCase(), clan.getClanName(), sender.getDisplayName().getFormattedText()).setStyle(TextStyles.GREEN));
                        if(ArrayUtils.contains(server.getPlayerList().getOnlinePlayerProfiles(), target)) {
                            EntityPlayerMP targetPlayer = CommandBase.getPlayer(server, sender, target.getName());
                            targetPlayer.sendMessage(TranslationUtil.getTranslation(targetPlayer.getUniqueID(), "commands.clan.promote.promoted", clan.getClanName(), clan.getMembers().get(target.getId()).toString().toLowerCase(), sender.getName()).setStyle(TextStyles.GREEN));
                        }
                    } else
                        sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.promote.error", target.getName()).setStyle(TextStyles.RED));
                } else
                    sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.common.player_not_in_clan", target.getName(), clan.getClanName()).setStyle(TextStyles.RED));
            } else
                sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.common.player_not_in_clan", target.getName(), clan.getClanName()).setStyle(TextStyles.RED));
        } else
            sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.common.playernotfound", playerName).setStyle(TextStyles.RED));
    }

    public static void demoteClanMember(MinecraftServer server, ICommandSender sender, String playerName, Clan clan) throws CommandException {
        GameProfile target = server.getPlayerProfileCache().getGameProfileForUsername(playerName);

        if(target != null) {
            if (!ClanCache.getPlayerClans(target.getId()).isEmpty()) {
                if (ClanCache.getPlayerClans(target.getId()).contains(clan)) {
                    if (clan.demoteMember(target.getId())) {
                        sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.demote.success", target.getName(), clan.getMembers().get(target.getId()).toString().toLowerCase(), clan.getClanName()).setStyle(TextStyles.GREEN));
                        if(ArrayUtils.contains(server.getPlayerList().getOnlinePlayerProfiles(), target)) {
                            EntityPlayerMP targetPlayer = CommandBase.getPlayer(server, sender, target.getName());
                            targetPlayer.sendMessage(TranslationUtil.getTranslation(targetPlayer.getUniqueID(), "commands.clan.demote.demoted", clan.getClanName(), clan.getMembers().get(target.getId()).toString().toLowerCase(), sender.getName()).setStyle(TextStyles.YELLOW));
                        }
                    } else
                        sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.demote.error", target.getName()).setStyle(TextStyles.RED));
                } else
                    sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.common.player_not_in_clan", target.getName(), clan.getClanName()).setStyle(TextStyles.RED));
            } else
                sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.common.player_not_in_clan", target.getName(), clan.getClanName()).setStyle(TextStyles.RED));
        } else
            sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.common.playernotfound", playerName).setStyle(TextStyles.RED));
    }

    public static void setRank(MinecraftServer server, ICommandSender sender, String playerName, Clan clan, EnumRank rank) throws CommandException {
        GameProfile target = server.getPlayerProfileCache().getGameProfileForUsername(playerName);

        if(target != null) {
            clan.addMember(target.getId(), rank);
            sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.opclan.setrank.success", target.getName(), rank.toString().toLowerCase(), clan.getClanName()).setStyle(TextStyles.GREEN));
            if(ArrayUtils.contains(server.getPlayerList().getOnlinePlayerProfiles(), target)) {
                EntityPlayerMP targetPlayer = CommandBase.getPlayer(server, sender, target.getName());
                if(targetPlayer != sender)
                    targetPlayer.sendMessage(TranslationUtil.getTranslation(targetPlayer.getUniqueID(), "commands.opclan.setrank.set", rank.toString().toLowerCase(), clan.getClanName(), sender.getName()).setStyle(TextStyles.YELLOW));
            }
        } else
            sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.common.playernotfound", playerName).setStyle(TextStyles.RED));
    }

    public static void kickMember(MinecraftServer server, ICommandSender sender, Clan selectedClan, GameProfile target) throws CommandException {
        if(selectedClan.removeMember(target.getId())) {
            sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.kick.success", target.getName(), selectedClan.getClanName()).setStyle(TextStyles.GREEN));
            selectedClan.messageAllOnline(sender instanceof EntityPlayerMP ? (EntityPlayerMP)sender : null, TextStyles.YELLOW, "commands.clan.kick.kicked_other", target.getName(), selectedClan.getClanName(), sender.getDisplayName().getFormattedText());
            if(ArrayUtils.contains(server.getPlayerList().getOnlinePlayerProfiles(), target)) {
                EntityPlayerMP targetPlayer = CommandBase.getPlayer(server, sender, target.getName());
                if(sender instanceof EntityPlayerMP && !((EntityPlayerMP) sender).getUniqueID().equals(target.getId()))
                    targetPlayer.sendMessage(TranslationUtil.getTranslation(targetPlayer.getUniqueID(), "commands.clan.kick.kicked", selectedClan.getClanName(), sender.getName()).setStyle(TextStyles.YELLOW));
                if(selectedClan.getClanId().equals(PlayerData.getDefaultClan(targetPlayer.getUniqueID())))
                    PlayerData.updateDefaultClan(targetPlayer.getUniqueID(), selectedClan.getClanId());
            }
        } else
            sender.sendMessage(TranslationUtil.getTranslation(sender, "commands.clan.kick.fail", target.getName(), selectedClan.getClanName()).setStyle(TextStyles.RED));
    }
}
