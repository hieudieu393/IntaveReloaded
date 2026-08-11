package de.jpx3.intave.check.movement;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration.CheckSettings;
import de.jpx3.intave.check.CheckSignalConfiguration;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.movement.timer.MicroBlink;
import de.jpx3.intave.check.movement.timer.PlayerTime;
import de.jpx3.intave.check.movement.timer.TickProtocolTimer;

public final class Timer extends Check {
  private final CheckViolationLevelDecrementer decrementer;

  private final boolean highToleranceMode;
  private final boolean reverseBlink;
  private final boolean reverseLag;
  private final boolean lowTolerance;
  private final boolean blinkSignalEnabled;
  private int blinkLimit;
  private final int timerTolerance;
  private final boolean detectPulseBlink;
  private final PlayerTime playerTime;
  private final MicroBlink microBlink;

  public Timer() {
    super("Timer", "timer");
    this.decrementer = new CheckViolationLevelDecrementer(this, 0.2);
    CheckSettings settings = configuration().settings();

    reverseBlink = settings.boolBy("reverse-blink", true);
    highToleranceMode = settings.boolBy("high-tolerance", false);
    lowTolerance = settings.boolBy("low-tolerance", false);
    reverseLag = settings.boolBy("reverse-lag", false);
    blinkSignalEnabled = CheckSignalConfiguration.enabled("timer", "blink");

    blinkLimit = settings.intBy("blink-limit", (lowTolerance ? 100 : -1));
    if (blinkLimit < 60 && blinkLimit >= 0) {
      blinkLimit = 60;
    }
    timerTolerance = settings.intBy("tolerance", 1);
    detectPulseBlink = settings.boolBy("block-pulse-blink", lowTolerance);

    this.playerTime = new PlayerTime(this);
    appendCheckPart(playerTime);

    this.microBlink = new MicroBlink(this);
    if (blinkSignalEnabled) {
      appendCheckPart(microBlink);
    }

    appendCheckPart(new TickProtocolTimer(this));
  }

  public void receiveMovement(ProtocolPacketEvent event) {
    playerTime.receiveMovement(event);
    if (blinkSignalEnabled) {
      microBlink.receiveMovement(event);
    }
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public boolean performLinkage() {
    return true;
  }

  public boolean highToleranceMode() {
    return highToleranceMode;
  }

  public boolean lowToleranceMode() {
    return lowTolerance;
  }

  public boolean reverseBlink() {
    return reverseBlink;
  }

  public boolean reverseLag() {
    return reverseLag;
  }

  public int blinkLimit() {
    return blinkLimit;
  }

  public int timerTolerance() {
    return timerTolerance;
  }

  public boolean detectPulseBlink() {
    return detectPulseBlink;
  }

  public CheckViolationLevelDecrementer decrementer() {
    return decrementer;
  }
}
