/*
 * DiscordSRV - A Minecraft to Discord and back link plugin
 * Copyright (C) 2016-2020 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package github.scarsz.discordsrv.objects.threads;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class NicknameUpdater extends Thread {

    private final Set<String> nonMembers = new HashSet<>();

    public NicknameUpdater() {
        setName("DiscordSRV - Nickname Updater");
    }

    @Override
    public void run() {
        while (true) {
            int rate = DiscordSRV.config().getInt("NicknameSynchronizationCycleTime");
            if (rate < 3) rate = 3;

            if (DiscordSRV.config().getBoolean("NicknameSynchronizationEnabled")) {
                DiscordSRV.debug("Synchronizing nicknames...");

                // Fix NPE with AccountLinkManager
                if (!DiscordSRV.isReady) {
                    try {
                        Thread.sleep(TimeUnit.MINUTES.toMillis(rate));
                    } catch (InterruptedException ignored) {
                        DiscordSRV.debug("Broke from Nickname Updater thread: sleep interrupted");
                        return;
                    }
                    continue;
                }

                Guild guild = DiscordSRV.getPlugin().getMainGuild();
                for (Player onlinePlayer : PlayerUtil.getOnlinePlayers()) {
                    // skip vanished players
                    if (PlayerUtil.isVanished(onlinePlayer)) continue;

                    String userId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(onlinePlayer.getUniqueId());
                    if (userId == null) continue;

                    User linkedUser = DiscordUtil.getJda().getUserById(userId);
                    if (linkedUser == null) continue;

                    if (guild.getMember(linkedUser) != null) nonMembers.remove(linkedUser.getId());
                    if (nonMembers.contains(linkedUser.getId())) continue;

                    // get the member, from cache if it's there otherwise from Discord
                    Member member;
                    try {
                        member = guild.retrieveMember(linkedUser, false).complete();
                    } catch (ErrorResponseException e) {
                        if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                            nonMembers.add(linkedUser.getId());
                            continue;
                        }
                        throw e;
                    }
                    if (member == null) {
                        DiscordSRV.debug(linkedUser + " is not in the Main guild, not setting nickname");
                        continue;
                    }

                    setNickname(member, onlinePlayer);
                }
            }

            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(rate));
            } catch (InterruptedException ignored) {
                DiscordSRV.debug("Broke from Nickname Updater thread: sleep interrupted");
                return;
            }
        }
    }

    public void setNickname(Member member, OfflinePlayer offlinePlayer) {
        String nickname;
        if (offlinePlayer.isOnline()) {
            Player player = offlinePlayer.getPlayer();

            nickname = DiscordSRV.config().getString("NicknameSynchronizationFormat")
                    .replace("%displayname%", player.getDisplayName() != null ? player.getDisplayName() : player.getName())
                    .replace("%username%", player.getName());

            nickname = PlaceholderUtil.replacePlaceholders(nickname, player);
        } else {
            nickname = offlinePlayer.getName();
        }

        nickname = DiscordUtil.strip(nickname);
        DiscordUtil.setNickname(member, nickname);
    }
}
