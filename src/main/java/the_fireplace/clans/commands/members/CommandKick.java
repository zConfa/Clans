package the_fireplace.clans.commands.members;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import org.apache.commons.lang3.ArrayUtils;
import the_fireplace.clans.clan.Clan;
import the_fireplace.clans.clan.ClanCache;
import the_fireplace.clans.clan.EnumRank;
import the_fireplace.clans.commands.ClanSubCommand;
import the_fireplace.clans.util.MinecraftColors;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CommandKick extends ClanSubCommand {
	@Override
	public EnumRank getRequiredClanRank() {
		return EnumRank.ADMIN;
	}

	@Override
	public int getMinArgs() {
		return 1;
	}

	@Override
	public int getMaxArgs() {
		return 1;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/clan kick <player>";
	}

	@SuppressWarnings("Duplicates")
	@Override
	public void run(MinecraftServer server, EntityPlayerMP sender, String[] args) throws CommandException {
		GameProfile target = server.getPlayerProfileCache().getGameProfileForUsername(args[0]);

		if(target != null) {
			if (!ClanCache.getPlayerClans(target.getId()).isEmpty()) {
				if (ClanCache.getPlayerClans(target.getId()).contains(selectedClan)) {//TODO verify
					EnumRank senderRank = selectedClan.getMembers().get(sender.getUniqueID());
					EnumRank targetRank = selectedClan.getMembers().get(target.getId());
					if (senderRank == EnumRank.LEADER) {
						removeMember(server, sender, selectedClan, target);
					} else if (targetRank == EnumRank.MEMBER) {
						removeMember(server, sender, selectedClan, target);
					} else
						sender.sendMessage(new TextComponentTranslation(MinecraftColors.RED + "You do not have the authority to kick out %s.", target.getName()));
				} else
					sender.sendMessage(new TextComponentTranslation(MinecraftColors.RED + "The player %s is not in your clan.", target.getName()));
			} else
				sender.sendMessage(new TextComponentTranslation(MinecraftColors.RED + "The player %s is not in your clan.", target.getName()));
		} else
			sender.sendMessage(new TextComponentTranslation(MinecraftColors.RED + "The player %s was not found.", args[0]));
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		ArrayList<String> playerNames = Lists.newArrayList();
		for(UUID player: selectedClan.getMembers().keySet()) {
			GameProfile playerProf = server.getPlayerProfileCache().getProfileByUUID(player);
			if(playerProf != null && (selectedClan.getMembers().get(player).equals(EnumRank.MEMBER) || (sender instanceof EntityPlayerMP && selectedClan.getMembers().get(((EntityPlayerMP) sender).getUniqueID()).equals(EnumRank.LEADER))))
				playerNames.add(playerProf.getName());
		}
		return args.length == 1 ? playerNames : Collections.emptyList();
	}

	private void removeMember(MinecraftServer server, EntityPlayerMP sender, Clan playerClan, GameProfile target) throws CommandException {
		if(playerClan.removeMember(target.getId())) {
			sender.sendMessage(new TextComponentTranslation(MinecraftColors.GREEN + "You have kicked %s out of the clan.", target.getName()));
			if(ArrayUtils.contains(server.getPlayerList().getOnlinePlayerProfiles(), target))
				getPlayer(server, sender, target.getName()).sendMessage(new TextComponentTranslation(MinecraftColors.GREEN + "You have been kicked out of %s by %s.", playerClan.getClanName(), sender.getName()));
		} else //Internal error because this should be unreachable
			sender.sendMessage(new TextComponentTranslation(MinecraftColors.RED + "Internal Error: The player %s is not in your clan.", target.getName()));
	}
}
