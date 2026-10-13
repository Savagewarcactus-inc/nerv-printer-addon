package com.julflips.nerv_printer.interfaces;


public interface IClientPlayerInteractionManager {
    void setBlockBreakingCooldown(int cooldown);

    float getCurrentBreakingProgress();
}
