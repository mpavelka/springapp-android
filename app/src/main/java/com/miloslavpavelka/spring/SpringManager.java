package com.miloslavpavelka.spring;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.util.Calendar;

/**
 * Created by mpavelka on 01/07/2018.
 */

public class SpringManager {
    public static final String TAG = "SpringManager";
    public static final int NOTIFY_WITH_DEFICIT_ML = 250;
    Context context;

    static final String
            SHARED_PREF_NAME = "springmgr",
            SP_FROM_HOUR_OF_DAY = "fromHourOfDay",
            SP_FROM_MINUTE = "fromMinute",
            SP_TO_HOUR_OF_DAY = "toHourOfDay",
            SP_TO_MINUTE = "toMinute",
            SP_CONSUMED_ML = "consumedMl",
            SP_DAILY_PLAN_ML = "dailyPlanMl";

    private int
            dailyPlanMl,
            consumedMl,
            deficitMl,
            prevConsumedMl,
            prevDeficitMl,
            planFromHourOfDay,
            planFromMinute,
            planToHourOfDay,
            planToMinute;

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;

    SpringManager(Context context) {
        this.context = context;
        this.deficitMl = 0;
        this.alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(context, SpringAlarmReceiver.class);
        this.alarmIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);
        this.reset();
    }

    // Setters
    public void setDailyPlanMl(int ml) {
        dailyPlanMl = ml;
    }
    public void setConsumedMl(int ml) {
        prevConsumedMl = consumedMl;
        consumedMl = ml;
    }
    public void setPlanFrom(int hourOfDay, int minute) {
        planFromHourOfDay = hourOfDay;
        planFromMinute = minute;
    }
    public void setPlanTo(int hourOfDay, int minute) {
        planToHourOfDay = hourOfDay;
        planToMinute = minute;
    }
    public void drinkMl(int ml) {
        setConsumedMl(getConsumedMl()+ml);
    }
    protected void setDeficitMl(int ml) {
        prevDeficitMl = deficitMl;
        deficitMl = ml;
    }

    public void evaluate() {
        setDeficitMl(computeDeficitMl());
        // With newly set deficit, reschedule next alarm
        rescheduleAlarm();
    }

    private void rescheduleAlarm() {
        Log.d(TAG, "Rescheduling alarm");
        // Cancel alarm
        alarmMgr.cancel(alarmIntent);

        // Compute next notification time
        int minutesToAlarm,
            elapsedMinutes = getElapsedMinutes(),
            planMinutesRange = getPlanMinutesRange();

        Log.d(TAG, "elapsedMinutes="+Integer.toString(elapsedMinutes));
        Log.d(TAG, "planMinutesRange="+Integer.toString(planMinutesRange));

        if (elapsedMinutes > planMinutesRange) {
            Log.d(TAG, "No alarm scheduled.");
            return;
        }
        else if (elapsedMinutes <= 0) {
            minutesToAlarm = -elapsedMinutes + getElapsedMinutesForConsumedMl(NOTIFY_WITH_DEFICIT_ML, dailyPlanMl, planMinutesRange);
        }
        else {
            int idealConsumedMl = getIdealConsumedMl(dailyPlanMl,getPlanMinutesRange(),elapsedMinutes);
            int elapsedMinutesForNextNotification = getElapsedMinutesForConsumedMl(
                idealConsumedMl-deficitMl+NOTIFY_WITH_DEFICIT_ML,
                dailyPlanMl,
                getPlanMinutesRange()
            );
            minutesToAlarm = elapsedMinutesForNextNotification - elapsedMinutes;
        }

        Log.d(TAG, "Scheduling alarm in "+Integer.toString(minutesToAlarm)+" minutes.");

        // Set alarm
        alarmMgr.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + minutesToAlarm * 60 * 1000,
            alarmIntent);
    }

    // Getters
    public int getDailyPlanMl() {
        return dailyPlanMl;
    }
    public int getPrevConsumedMl() {
        return prevConsumedMl;
    }
    public int getPrevDeficitMl() {
        return prevDeficitMl;
    }
    public int getConsumedMl() {
        return consumedMl;
    }
    public int getDeficitMl() {
        return deficitMl;
    }
    public int getPlanFromHourOfDay() {
        return planFromHourOfDay;
    }
    public int getPlanFromMinute() {
        return planFromMinute;
    }
    public int getPlanToHourOfDay() {
        return planToHourOfDay;
    }
    public int getPlanToMinute() {
        return planToMinute;
    }
    public float getConsumedPlanRatio() {
        if (dailyPlanMl == 0)
            return (float)1;
        return (float)consumedMl/(float)dailyPlanMl;
    }

    private SharedPreferences getPreferences() {
        return this.context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
    }

    void reset() {
        this.dailyPlanMl = 0;
        this.prevConsumedMl = this.consumedMl;
        this.prevDeficitMl = this.deficitMl;
        this.consumedMl = 0;
        this.deficitMl = 0;
        this.planFromHourOfDay = 0;
        this.planFromMinute = 0;
        this.planToHourOfDay = 0;
        this.planToMinute = 0;
    }

    void load() {
        SharedPreferences prefs = this.getPreferences();
        planFromHourOfDay = prefs.getInt(SP_FROM_HOUR_OF_DAY, 8);
        planFromMinute = prefs.getInt(SP_FROM_MINUTE, 0);
        planToHourOfDay = prefs.getInt(SP_TO_HOUR_OF_DAY, 21);
        planToMinute = prefs.getInt(SP_TO_MINUTE, 0);
        consumedMl = prefs.getInt(SP_CONSUMED_ML, 0);
        dailyPlanMl = prefs.getInt(SP_DAILY_PLAN_ML, 2500);

        // Compute deficit
        this.deficitMl = this.computeDeficitMl();
    }

    void store() {
        SharedPreferences prefs = this.getPreferences();
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt(SP_FROM_HOUR_OF_DAY, planFromHourOfDay);
        editor.putInt(SP_FROM_MINUTE, planFromMinute);
        editor.putInt(SP_TO_HOUR_OF_DAY, planToHourOfDay);
        editor.putInt(SP_TO_MINUTE, planToMinute);
        editor.putInt(SP_CONSUMED_ML, consumedMl);
        editor.putInt(SP_DAILY_PLAN_ML, dailyPlanMl);

        editor.commit();
    }

    int computeDeficitMl() {
        int deficitMl,
            idealConsumedMl,
            elapsedMinutes = getElapsedMinutes(),
            planMinutesRange = getPlanMinutesRange();

        // Compute
        idealConsumedMl = getIdealConsumedMl(dailyPlanMl, planMinutesRange, elapsedMinutes);
        deficitMl = idealConsumedMl - consumedMl;

        // Return
        if (elapsedMinutes < 0)
            return 0;
        if (elapsedMinutes > planMinutesRange)
            return 0;
        if (deficitMl <= 0)
            return 0;
        return deficitMl;
    }

    int getElapsedMinutes() {
        Calendar cal = Calendar.getInstance();
        int currentHourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                currentMinute    = cal.get(Calendar.MINUTE);
        return 60*(currentHourOfDay-this.planFromHourOfDay) - this.planFromMinute + currentMinute;
    }

    int getPlanMinutesRange() {
        return 60*(planToHourOfDay-planFromHourOfDay) - planFromMinute + planToMinute;
    }

    int getIdealConsumedMl(int dailyPlanMl, int planMinutesRange, int elapsedMinutes) {
        // Utilizes linear interpolation
        return (int)(dailyPlanMl * ((float)elapsedMinutes/(float)planMinutesRange));
    }

    int getElapsedMinutesForConsumedMl(int consumedMl, int dailyPlanMl, int planMinutesRange) {
        return (int)((float)(consumedMl*planMinutesRange)/(float)(dailyPlanMl));
    }
}
