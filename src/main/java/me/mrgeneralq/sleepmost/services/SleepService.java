package me.mrgeneralq.sleepmost.services;
import me.clip.placeholderapi.PlaceholderAPI;
import me.mrgeneralq.sleepmost.enums.SleepCalculationType;
import me.mrgeneralq.sleepmost.flags.CalculationMethodFlag;
import me.mrgeneralq.sleepmost.flags.PlayersRequiredFlag;
import me.mrgeneralq.sleepmost.flags.UseAfkFlag;
import me.mrgeneralq.sleepmost.flags.serialization.SleepCalculationTypeSerialization;
import me.mrgeneralq.sleepmost.interfaces.*;
import me.mrgeneralq.sleepmost.statics.DataContainer;
import me.mrgeneralq.sleepmost.statics.ServerVersion;
import me.mrgeneralq.sleepmost.enums.SleepSkipCause;
import me.mrgeneralq.sleepmost.events.SleepSkipEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static java.util.stream.Collectors.toList;

import java.util.List;

public class SleepService implements ISleepService {

    private final IConfigRepository configRepository;
    private final IConfigService configService;

    private final CalculationMethodFlag calculationMethodFlag;
    private final PlayersRequiredFlag playersRequiredFlag;
    private final UseAfkFlag useAfkFlag;

    private static final int
            NIGHT_START_TIME = 12541,
            NIGHT_END_TIME = 23850;

    public SleepService(IConfigService configService, IConfigRepository configRepository, CalculationMethodFlag calculationMethodFlag, PlayersRequiredFlag playersRequiredFlag, UseAfkFlag useAfkFlag){
        this.configService = configService;
        this.configRepository = configRepository;

        this.calculationMethodFlag = calculationMethodFlag;
        this.playersRequiredFlag = playersRequiredFlag;
        this.useAfkFlag = useAfkFlag;
    }

    @Override
    public boolean enabledForWorld(World world) {
       return configRepository.containsWorld(world);
    }

    @Override
    public boolean sleepPercentageReached(World world) {

        return getPlayersSleepingCount(world) >= getRequiredPlayersSleepingCount(world);
    }

    @Override
    public double getPercentageRequired(World world) {
        return configRepository.getPercentageRequired(world);
    }

    @Override
    public boolean getMobNoTarget(World world) {
        return configRepository.getMobNoTarget(world);
    }

    @Override
    public double getSleepingPlayerPercentage(World world) {
        return getPlayersSleepingCount(world) / getPlayerCountInWorld(world);
    }

    @Override
    public int getPlayersSleepingCount(World world) {
        if(ServerVersion.CURRENT_VERSION.sleepCalculatedDifferently()){
            return DataContainer.getContainer().getSleepingPlayers(world).size();
        }
        else {
            return (int) (world.getPlayers().stream().filter(Player::isSleeping).count()+1);
        }

    }

    @Override
    public int getRequiredPlayersSleepingCount(World world) {

        /*SleepCalculationType sleepCalculationType = SleepCalculationType.PLAYERS_REQUIRED;

        try{
            String enumName = String.format("%s%s", this.calculationMethodFlag.getValueAt(world), "_REQUIRED");
            enumName = enumName.toUpperCase();

            sleepCalculationType = SleepCalculationType.valueOf(enumName);
        }
        catch (Exception ex){}*/

        int requiredCount;

        switch (this.calculationMethodFlag.getValueAt(world))
        {
            case PERCENTAGE_REQUIRED:
                requiredCount =  (int) Math.ceil(getPlayerCountInWorld(world) * getPercentageRequired(world));
                break;
            case PLAYERS_REQUIRED:
                int requiredPlayersInConfig = this.playersRequiredFlag.getValueAt(world);
                requiredCount = (requiredPlayersInConfig <= getPlayerCountInWorld(world)) ? requiredPlayersInConfig: getPlayerCountInWorld(world);
                break;
            default:
                requiredCount = 0;
        }

        return requiredCount;
    }

    @Override
    public int getPlayerCountInWorld(World world) {

        //full list of players
        List<Player> allPlayers = world.getPlayers();

        //check if exempt flag is enabled
        if(configRepository.getUseExempt(world)){
            allPlayers = allPlayers.stream()
                    .filter(p -> !p.hasPermission("sleepmost.exempt"))
                    .collect(toList());
        }
        boolean afkFlagEnabled = this.useAfkFlag.getValueAt(world);

        //check if user is afk
        if(afkFlagEnabled && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && Bukkit.getPluginManager().getPlugin("Essentials") != null)
               allPlayers = allPlayers.stream()
                       .filter(p -> PlaceholderAPI.setPlaceholders(p, "%essentials_afk%").equalsIgnoreCase("no"))
                       .collect(toList());

            return (allPlayers.size() > 0) ? allPlayers.size() : 1;
    }

    @Override
    public void resetDay(World world, String lastSleeperName, String lastSleeperDisplayName) {
        SleepSkipCause cause = SleepSkipCause.UNKNOWN;

        if (this.isNight(world)) {
            cause = SleepSkipCause.NIGHT_TIME;
            world.setTime(configService.getResetTime());
        } else if(world.isThundering()){
            cause = SleepSkipCause.STORM;
        }
        
        world.setThundering(false);
        world.setStorm(false);
        Bukkit.getServer().getPluginManager().callEvent(new SleepSkipEvent(world, cause, lastSleeperName, lastSleeperDisplayName));
    }

    @Override
    public boolean resetRequired(World world) {
    	return isNight(world) || world.isThundering();
    }

    @Override
    public boolean isNight(World world) {
        return world.getTime() > NIGHT_START_TIME && world.getTime() < NIGHT_END_TIME;
    }

    @Override
    public SleepSkipCause getSleepSkipCause(World world) {
    	return isNight(world) ? SleepSkipCause.NIGHT_TIME : SleepSkipCause.STORM;
    }

    @Override
    public void reloadConfig() {
        configRepository.reloadConfig();
    }

    @Override
    public void enableForWorld(World world) {
        configRepository.addWorld(world);
    }

    @Override
    public void disableForWorld(World world) {
        configRepository.disableForWorld(world);
    }
}
