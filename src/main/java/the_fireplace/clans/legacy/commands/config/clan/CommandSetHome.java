package the_fireplace.clans.legacy.commands.config.clan;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.chunk.Chunk;
import the_fireplace.clans.clan.home.ClanHomes;
import the_fireplace.clans.legacy.ClansModContainer;
import the_fireplace.clans.legacy.api.ClaimAccessor;
import the_fireplace.clans.legacy.commands.ClanSubCommand;
import the_fireplace.clans.legacy.model.ChunkPositionWithData;
import the_fireplace.clans.legacy.model.EnumRank;
import the_fireplace.clans.legacy.util.TextStyles;
import the_fireplace.clans.legacy.util.translation.TranslationUtil;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CommandSetHome extends ClanSubCommand
{
    @Override
    public String getName() {
        return "sethome";
    }

    @Override
    public EnumRank getRequiredClanRank() {
        return EnumRank.LEADER;
    }

    @Override
    public int getMinArgs() {
        return 0;
    }

    @Override
    public int getMaxArgs() {
        return 0;
    }

    @Override
    public void run(MinecraftServer server, EntityPlayerMP sender, String[] args) {
        Chunk c = sender.getEntityWorld().getChunk(sender.getPosition());
        if (selectedClan.equals(ClaimAccessor.getInstance().getChunkClan(new ChunkPositionWithData(c)))) {
            if (ClanHomes.hasHome(selectedClan)
                ? ClanHomes.isHomeWithinRadiusExcluding(sender.getPosition(), ClansModContainer.getConfig().getMinClanHomeDist(), ClanHomes.get(selectedClan).toBlockPos())
                : ClanHomes.isHomeWithinRadius(sender.getPosition(), ClansModContainer.getConfig().getMinClanHomeDist())) {
                sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.sethome.proximity", ClansModContainer.getConfig().getMinClanHomeDist()).setStyle(TextStyles.RED));
                return;
            }
            ClanHomes.set(selectedClan, sender.getPosition(), sender.dimension);
            sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.sethome.success", selectedClanName).setStyle(TextStyles.GREEN));
        } else {
            sender.sendMessage(TranslationUtil.getTranslation(sender.getUniqueID(), "commands.clan.sethome.territory").setStyle(TextStyles.RED));
        }
    }
}
