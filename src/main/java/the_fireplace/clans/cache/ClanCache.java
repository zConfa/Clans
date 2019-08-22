package the_fireplace.clans.cache;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import the_fireplace.clans.commands.CommandClan;
import the_fireplace.clans.commands.CommandOpClan;
import the_fireplace.clans.commands.CommandRaid;
import the_fireplace.clans.data.ClanDatabase;
import the_fireplace.clans.model.Clan;
import the_fireplace.clans.model.EnumRank;
import the_fireplace.clans.util.TextStyles;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ClanCache {
	private static Map<UUID, List<Clan>> playerClans = Maps.newHashMap();
	private static Map<String, Clan> clanNames = Maps.newHashMap();
	private static List<String> clanBanners = Lists.newArrayList();
	private static Map<UUID, Clan> clanInvites = Maps.newHashMap();
	private static Map<Clan, BlockPos> clanHomes = Maps.newHashMap();

	private static List<UUID> buildAdmins = Lists.newArrayList();
	public static Map<UUID, Clan> clanChattingPlayers = Maps.newHashMap();

	//Maps of (Player Unique ID) -> (Clan)
	public static Map<UUID, Clan> autoAbandonClaims = Maps.newHashMap();
	public static Map<UUID, Clan> autoClaimLands = Maps.newHashMap();
	public static List<UUID> opAutoAbandonClaims = Lists.newArrayList();
	public static Map<UUID, Clan> opAutoClaimLands = Maps.newHashMap();

	public static final List<String> forbiddenClanNames = Lists.newArrayList("wilderness", "underground", "opclan", "clan", "raid", "null");
	static {
		forbiddenClanNames.addAll(CommandClan.commands.keySet());
		forbiddenClanNames.addAll(CommandClan.aliases.keySet());
		forbiddenClanNames.addAll(CommandOpClan.commands.keySet());
		forbiddenClanNames.addAll(CommandOpClan.aliases.keySet());
		forbiddenClanNames.addAll(CommandRaid.commands.keySet());
		forbiddenClanNames.addAll(CommandRaid.aliases.keySet());
	}

	@Nullable
	public static Clan getClanById(@Nullable UUID clanID){
		return ClanDatabase.getClan(clanID);
	}

	@Nullable
	public static Clan getClanByName(String clanName){
		if(clanNames.isEmpty())
			for(Clan clan: ClanDatabase.getClans())
				clanNames.put(clan.getClanName().toLowerCase(), clan);
		return clanNames.get(clanName.toLowerCase());
	}

	public static List<Clan> getPlayerClans(@Nullable UUID player) {
		if(player == null)
			return Collections.unmodifiableList(Lists.newArrayList());
		if(playerClans.containsKey(player))
			return (playerClans.get(player) != null ? playerClans.get(player) : Lists.newArrayList());
		playerClans.put(player, ClanDatabase.lookupPlayerClans(player));
		return (playerClans.get(player) != null ? Collections.unmodifiableList(playerClans.get(player)) : Collections.unmodifiableList(Lists.newArrayList()));
	}

	public static EnumRank getPlayerRank(UUID player, Clan clan) {
		return clan.getMembers().get(player);
	}

	public static boolean clanNameTaken(String clanName) {
		clanName = clanName.toLowerCase();
		if(clanNames.isEmpty())
			for(Clan clan: ClanDatabase.getClans())
				clanNames.put(clan.getClanName().toLowerCase(), clan);
		return forbiddenClanNames.contains(clanName) || clanNames.containsKey(clanName);
	}

	public static boolean clanBannerTaken(String clanBanner) {
		if(clanBanners.isEmpty())
			for(Clan clan: ClanDatabase.getClans())
				if(clan.getClanBanner() != null)
					clanBanners.add(clan.getClanBanner().toLowerCase());
		return clanBanners.contains(clanBanner.toLowerCase());
	}

	public static void addBanner(String banner) {
		if(clanBanners.isEmpty())
			for(Clan clan: ClanDatabase.getClans())
				if(clan.getClanBanner() != null)
					clanBanners.add(clan.getClanBanner().toLowerCase());
		clanBanners.add(banner.toLowerCase());
	}

	public static void removeBanner(@Nullable String banner){
		if(banner != null)
			clanBanners.remove(banner.toLowerCase());
	}

	public static Map<String, Clan> getClanNames() {
		return Collections.unmodifiableMap(clanNames);
	}

	public static void addName(Clan nameClan){
		if(clanNames.isEmpty())
			for(Clan clan: ClanDatabase.getClans())
				clanNames.put(TextStyles.stripFormatting(clan.getClanName().toLowerCase()), clan);
		clanNames.put(TextStyles.stripFormatting(nameClan.getClanName().toLowerCase()), nameClan);
	}

	public static void removeName(String name){
		clanNames.remove(TextStyles.stripFormatting(name.toLowerCase()));
	}

	public static boolean inviteToClan(UUID player, Clan clan) {
		if(!clanInvites.containsKey(player)) {
			clanInvites.put(player, clan);
			return true;
		}
		return false;
	}

	public static void addPlayerClan(UUID player, Clan clan) {
		getPlayerClans(player);
		playerClans.get(player).add(clan);
	}

	public static void removePlayerClan(UUID player, Clan clan) {
		getPlayerClans(player);
		playerClans.get(player).remove(clan);
	}

	@Nullable
	public static Clan getInvite(UUID player) {
		return clanInvites.get(player);
	}

	@Nullable
	public static Clan removeInvite(UUID player) {
		return clanInvites.remove(player);
	}

	public static Map<Clan, BlockPos> getClanHomes() {
		if(clanHomes.isEmpty())
			for(Clan clan: ClanDatabase.getClans())
				if(clan.hasHome())
					clanHomes.put(clan, clan.getHome());
		return Collections.unmodifiableMap(clanHomes);
	}

	public static void setClanHome(Clan c, BlockPos home) {
		if(clanHomes.isEmpty())
			for(Clan clan: ClanDatabase.getClans())
				clanHomes.put(clan, clan.getHome());
		clanHomes.put(c, home);
	}

	public static void clearClanHome(Clan c) {
		clanHomes.remove(c);
	}

	public static void removeClan(Clan c) {
		clearClanHome(c);
		for(Map.Entry<UUID, Clan> clanInvite: clanInvites.entrySet())
			if(clanInvite.getValue().equals(c))
				clanInvites.remove(clanInvite.getKey());
		for(UUID player: playerClans.keySet())
			removePlayerClan(player, c);
		removeName(c.getClanName());
		removeBanner(c.getClanBanner());
	}

	public static boolean toggleClaimAdmin(EntityPlayerMP admin){
		if(buildAdmins.contains(admin.getUniqueID())) {
			buildAdmins.remove(admin.getUniqueID());
			return false;
		} else {
			buildAdmins.add(admin.getUniqueID());
			return true;
		}
	}

	public static boolean isClaimAdmin(EntityPlayerMP admin) {
		return buildAdmins.contains(admin.getUniqueID());
	}

	public static void updateChat(UUID uuid, Clan clan) {
		if(clanChattingPlayers.containsKey(uuid) && clanChattingPlayers.get(uuid).equals(clan))
			clanChattingPlayers.remove(uuid);
		else
			clanChattingPlayers.put(uuid, clan);
	}
}
