package com.nya_wsl.alchemy;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /alchemy reload}：重新读取 config/alchemy-common.toml，立即生效，不用重启游戏。
 * 需要管理员权限（权限等级 2，和 /reload 一致）。
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlchemyCommand
{
    private AlchemyCommand()
    {
    }

    @SubscribeEvent
    static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
                Commands.literal("alchemy")
                        .then(Commands.literal("reload")
                                // 单人存档里允许直接使用（关闭作弊也能用），服务器上仍要求管理员权限
                                .requires(source -> source.getServer().isSingleplayer() || source.hasPermission(2))
                                .executes(AlchemyCommand::reload)));
    }

    private static int reload(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();

        if (!Config.reloadFromDisk())
        {
            source.sendFailure(Component.translatable("commands.alchemy.reload.failed"));
            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable("commands.alchemy.reload.success", Config.currentValues()),
                true);
        return 1;
    }
}
