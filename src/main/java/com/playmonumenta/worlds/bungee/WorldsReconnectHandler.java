package com.playmonumenta.worlds.bungee;

import com.playmonumenta.redissync.ConfigAPI;
import com.playmonumenta.redissync.RedisAPI;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nullable;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ReconnectHandler;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class WorldsReconnectHandler implements ReconnectHandler {

	private final @Nullable String mDefaultServer;

	private static final class ShardInfo {
		final String mShardName;
		final String mShardScoreboard;
		final boolean mShardSticky;

		public String getName() {
			return mShardName;
		}

		public String getScoreboard() {
			return mShardScoreboard;
		}

		public boolean isSticky() {
			return mShardSticky;
		}

		public ShardInfo(String name, String scoreboard, boolean sticky) {
			mShardName = name;
			mShardScoreboard = scoreboard;
			mShardSticky = sticky;
		}
	}

	/* TODO: This needs to be populated via config & reloadable */
	private static final Map<String, ShardInfo> mShardsInfo = new HashMap<>();
	static {
		mShardsInfo.put("white", new ShardInfo("white", "D1Access", true));
	}

	public WorldsReconnectHandler(@Nullable String defaultServer) {
		mDefaultServer = defaultServer;
	}

	@Override
	public @Nullable ServerInfo getServer(ProxiedPlayer player) {
		ServerInfo server = null;

		String storedServerName = null;
		final Map<String, Integer> playerScores;
		UUID uuid = player.getUniqueId();

		try {
			RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();

			RedisFuture<String> nameFuture = commands.hget(locationsKey(), uuid.toString());
			RedisFuture<String> scoresFuture = commands.lindex(MonumentaRedisSyncAPI.getRedisScoresPath(uuid), 0);

			storedServerName = nameFuture.get(6, TimeUnit.SECONDS);
			String playerScoresStr = scoresFuture.get(6, TimeUnit.SECONDS);
			if (playerScoresStr == null || playerScoresStr.isEmpty()) {
				// New players may not have any scores
				playerScores = new HashMap<>();
			} else {
				playerScores = new Gson().fromJson(playerScoresStr, JsonObject.class).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().getAsInt()));
			}
		} catch (Exception ex) {
			ProxyServer.getInstance().getLogger().log(Level.WARNING, "Exception while getting player location for '" + player.getName() + "': " + ex.getMessage());
			ex.printStackTrace();
			/* TODO: Should kick the player if they throw a (likely timeout?) exception */
			return null;
		}



		if (storedServerName == null) {
			/* Player has never connected before */
			if (mDefaultServer != null) {
				/* default server */
				server = ProxyServer.getInstance().getServerInfo(mDefaultServer);
			}
			/*
			 * If mDefaultServer is null, no default specified - let
			 * bungee handle this based on its own config file
			 */
		} else {
			ShardInfo shardInfo = mShardsInfo.get(storedServerName);
			if (shardInfo == null) {
				// TODO: This is pretty bad, needs better error handling. Probably kick player with an error?
				ProxyServer.getInstance().getLogger().log(Level.WARNING,
						"Missing shard info for player '" + player.getName() + "'. Requested shard '" + storedServerName + "'");
				return null;
			}

			if (shardInfo.isSticky()) {
				/* The shard is sticky - i.e. a dungeon. Need to make sure to connect them back to the right instance or none at all */

				/* Get the player's current instance on this sticky shard, or 0 in case their instance was deleted or didn't exist */
				int instance = playerScores.getOrDefault(shardInfo.getScoreboard(), 0);

				/* TODO: Need to use heartbeat data to figure out which shard */
			} else {
				/* The shard is not sticky - i.e. an overworld instance. Prefer to connect them to the same one if possible, otherwise pick the next best */

			}


			/* Player has connected before */
			server = ProxyServer.getInstance().getServerInfo(storedServerName);
			if (server == null) {
				ProxyServer.getInstance().getLogger().log(Level.WARNING,
						"Failed to connect player '" + player.getName() + "' to last server '" + storedServerName + "'");
			}
		}

		return server;
	}

	@Override
	public void setServer(ProxiedPlayer player) {
		String reconnectServer = (player.getReconnectServer() != null) ? player.getReconnectServer().getName() : player.getServer().getInfo().getName();
		RedisAPI.getInstance().async().hset(locationsKey(), player.getUniqueId().toString(), reconnectServer);
	}

	private String locationsKey() {
		return String.format("%s:bungee:locations", ConfigAPI.getServerDomain());
	}

	@Override
	public void close() {
	}

	@Override
	public void save() {
	}
}
