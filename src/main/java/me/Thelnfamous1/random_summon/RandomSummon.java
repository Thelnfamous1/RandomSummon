package me.Thelnfamous1.random_summon;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(RandomSummon.MODID)
public class RandomSummon {
    public static final String MODID = "random_summon";
    public static final Logger LOGGER = LogUtils.getLogger();
    public RandomSummon() {
        //Configurator.setLevel("net.minecraft.commands", Level.DEBUG);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event){
        RandomSummonCommand.register(event.getDispatcher());
    }
}
