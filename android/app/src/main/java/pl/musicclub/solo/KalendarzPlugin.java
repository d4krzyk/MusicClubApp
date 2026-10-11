package pl.musicclub.solo;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.CalendarContract;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * "Dodaj do kalendarza": otwiera ekran nowego wydarzenia w Kalendarzu Google z wypelnionymi polami (nazwa, miejsce,
 * opis, poczatek i koniec). Uzytkownik tylko zatwierdza - aplikacja nie potrzebuje uprawnien do kalendarza, bo sama
 * niczego w nim nie zapisuje. Gdy Kalendarza Google nie ma, Android pokazuje inne aplikacje kalendarza.
 */
@CapacitorPlugin(name = "Kalendarz")
public class KalendarzPlugin extends Plugin {

    static final String KALENDARZ_GOOGLE = "com.google.android.calendar";

    @PluginMethod
    public void dodaj(PluginCall call) {
        Double begin = call.getDouble("begin");
        Double end = call.getDouble("end");
        if (begin == null || end == null) {
            call.reject("Brak czasu wydarzenia");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, call.getString("title", ""))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, call.getString("location", ""))
            .putExtra(CalendarContract.Events.DESCRIPTION, call.getString("description", ""))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.longValue())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.longValue())
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, Boolean.TRUE.equals(call.getBoolean("allDay", false)));

        Intent google = new Intent(intent).setPackage(KALENDARZ_GOOGLE);
        try {
            getActivity().startActivity(google);
            call.resolve();
            return;
        } catch (ActivityNotFoundException brakGoogle) {
            // nie ma Kalendarza Google - niech wybierze inny kalendarz
        }
        try {
            getActivity().startActivity(Intent.createChooser(intent, call.getString("chooserTitle", "")));
            call.resolve();
        } catch (ActivityNotFoundException brak) {
            call.reject("Brak aplikacji kalendarza", "NO_CALENDAR");
        }
    }
}
