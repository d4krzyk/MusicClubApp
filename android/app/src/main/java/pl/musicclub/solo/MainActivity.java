package pl.musicclub.solo;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Wlasna wtyczka musi byc zarejestrowana przed startem mostka (super.onCreate).
        registerPlugin(KalendarzPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
