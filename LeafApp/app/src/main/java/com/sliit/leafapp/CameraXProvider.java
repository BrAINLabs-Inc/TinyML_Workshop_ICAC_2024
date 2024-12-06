package com.sliit.leafapp;

import androidx.camera.core.CameraXConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.camera2.Camera2Config;

public class CameraXProvider implements CameraXConfig.Provider {
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }
}