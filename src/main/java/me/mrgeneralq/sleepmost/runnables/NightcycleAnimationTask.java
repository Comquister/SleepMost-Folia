package me.mrgeneralq.sleepmost.runnables;

import me.mrgeneralq.sleepmost.enums.MessageKey;
import me.mrgeneralq.sleepmost.enums.SleepSkipCause;
import me.mrgeneralq.sleepmost.interfaces.IConfigService;
import me.mrgeneralq.sleepmost.interfaces.IFlagsRepository;
import me.mrgeneralq.sleepmost.interfaces.IMessageService;
import me.mrgeneralq.sleepmost.interfaces.ISleepMostWorldService;
import me.mrgeneralq.sleepmost.interfaces.ISleepService;
import me.mrgeneralq.sleepmost.models.SleepMostWorld;
import me.mrgeneralq.sleepmost.statics.DataContainer;
import me.mrgeneralq.sleepmost.statics.ServerVersion;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class NightcycleAnimationTask {

    private final ISleepService sleepService;
    private final IFlagsRepository flagsRepository;
    private final World world;
    private final String lastSleeperName;
    private final String lastSleeperDisplayName;
    private final SleepSkipCause skipCause;
    private final List<OfflinePlayer> peopleWhoSlept;
    private final ISleepMostWorldService sleepMostWorldService;
    private final IMessageService messageService;
    private final IConfigService configService;
    private int iterationCount = 1;
    private boolean cancelled = false;
    private final DataContainer dataContainer = DataContainer.getContainer();

    public NightcycleAnimationTask(
            ISleepService sleepService,
            IFlagsRepository flagsRepository,
            World world,
            Player lastSleeper,
            List<OfflinePlayer> peopleWhoSlept,
            SleepSkipCause sleepSkipCause,
            ISleepMostWorldService sleepMostWorldService,
            IMessageService messageService,
            IConfigService configService) {
        this.sleepService = sleepService;
        this.flagsRepository = flagsRepository;
        this.world = world;
        this.lastSleeperName = lastSleeper.getName();
        this.lastSleeperDisplayName = lastSleeper.getDisplayName();
        this.skipCause = sleepSkipCause;
        this.peopleWhoSlept = peopleWhoSlept;
        this.sleepMostWorldService = sleepMostWorldService;
        this.messageService = messageService;
        this.configService = configService;
    }

    // Este é o método que será chamado a cada tick.
    public void tick() {
        if (cancelled) return;

        // 85 por padrão
        final int baseSpeed = configService.getNightcycleAnimationSpeed();
        final int maxSpeed = configService.getNightcycleAnimationSpeedMax();
        int calculatedSpeed = baseSpeed;

        if (this.flagsRepository.getDynamicAnimationSpeedFlag().getValueAt(world)) {
            int sleepingPlayers = this.sleepService.getSleepersAmount(world);
            int minSleepingPlayers = this.sleepService.getRequiredSleepersCount(world);
            int totalPlayers = world.getPlayers().size();

            int numerator = Math.max(sleepingPlayers - minSleepingPlayers, 0);
            int denominator = Math.max(totalPlayers - minSleepingPlayers, 1);
            double additionalPlayersRatio = (double) numerator / denominator;

            calculatedSpeed = Math.min((int) Math.round((additionalPlayersRatio * (maxSpeed - baseSpeed)) + baseSpeed), maxSpeed);
        }

        world.setTime(world.getTime() + calculatedSpeed);

        // Verifica se deve cancelar a animação
        SleepMostWorld sleepMostWorld = this.sleepMostWorldService.getWorld(world);
        if (!this.flagsRepository.getForceNightcycleAnimationFlag().getValueAt(world)
                && !sleepService.isRequiredCountReached(world)
                && sleepService.isNight(world)) {
            sleepMostWorld.setTimeCycleAnimationIsRunning(false);
            cancel();
            return;
        }

        if (!sleepService.isNight(world)) {
            sleepMostWorld.setTimeCycleAnimationIsRunning(false);
            sleepService.executeSleepReset(world, this.lastSleeperName, this.lastSleeperDisplayName, this.peopleWhoSlept, this.skipCause);
            cancel();

            if (this.flagsRepository.getClockAnimationFlag().getValueAt(world)
                    && ServerVersion.CURRENT_VERSION.supportsTitles()
                    && skipCause == SleepSkipCause.NIGHT_TIME) {

                List<Player> playerList = (this.flagsRepository.getNonSleepingClockAnimationFlag().getValueAt(world)
                        ? world.getPlayers()
                        : peopleWhoSlept.stream().filter(OfflinePlayer::isOnline).map(OfflinePlayer::getPlayer).collect(Collectors.toList()));

                for (Player p : playerList) {
                    String clockTitle = this.messageService.getMessage(MessageKey.CLOCK_TITLE)
                            .setTime(0)
                            .build();

                    String clockSubTitle = this.messageService.getMessage(MessageKey.CLOCK_SUBTITLE)
                            .build();

                    p.sendTitle(clockTitle, clockSubTitle, 0, 70, 20);
                }
            }
        } else {
            // Durante a noite, atualiza o clock se estiver habilitado
            if (this.flagsRepository.getClockAnimationFlag().getValueAt(world)
                    && ServerVersion.CURRENT_VERSION.supportsTitles()) {
                List<Player> playerList = (this.flagsRepository.getNonSleepingClockAnimationFlag().getValueAt(world)
                        ? world.getPlayers()
                        : peopleWhoSlept.stream().filter(OfflinePlayer::isOnline).map(OfflinePlayer::getPlayer).collect(Collectors.toList()));

                for (Player p : playerList) {
                    String clockTitle = this.messageService.getMessage(MessageKey.CLOCK_TITLE)
                            .setTime((int) world.getTime())
                            .build();

                    String clockSubTitle = this.messageService.getMessage(MessageKey.CLOCK_SUBTITLE)
                            .build();

                    p.sendTitle(clockTitle, clockSubTitle, 0, 70, 20);
                }
            }
        }

        iterationCount++;
    }

    // Método pra cancelar a task.
    public void cancel() {
        this.cancelled = true;
    }

    // Útil pra checar se a task foi cancelada.
    public boolean isCancelled() {
        return this.cancelled;
    }
}
