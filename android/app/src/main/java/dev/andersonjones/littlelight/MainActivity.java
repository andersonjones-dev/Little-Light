package dev.andersonjones.littlelight;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

import dev.andersonjones.littlelight.guard.GuardPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(GuardPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
