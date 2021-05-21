/*
 * Copyright (C) 2020  Benjamin Huber
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package at.huber.raspicast.utils;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.TaskStackBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import at.huber.raspicast.R;
import at.huber.raspicast.RaspiListActivity;
import at.huber.raspicast.SshConnection;
import at.huber.raspicast.VideoControlExecutor;

public class FileStreamService extends Service{

	private static final int NOTIFICATION_ID = 0x42;

	private WakeLock wakelock;

	private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture future;
	
	@Override
	public void onCreate(){
		if(wakelock == null){
			PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
			wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getSimpleName());
			wakelock.setReferenceCounted(false);
		}
		
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		if(wakelock!=null)
			wakelock.acquire();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			NotificationChannel chan = new NotificationChannel(getPackageName(), getResources().getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
		}

		startForeground(NOTIFICATION_ID, buildNotification(this));
		runCastCheckExecutor();

		return START_STICKY;
	}

	private static Notification buildNotification(final Context context) {

		Intent stopCastIntent = new Intent(context, StopCastReceiver.class);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, stopCastIntent, PendingIntent.FLAG_ONE_SHOT );

		Intent castActIntent;
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		castActIntent = new Intent(context, RaspiListActivity.class);
		stackBuilder.addParentStack(RaspiListActivity.class);

		stackBuilder.addNextIntent(castActIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(context, context.getPackageName())
						.setSmallIcon(at.huber.raspicast.R.drawable.ic_cast_white_36dp)
						.setContentTitle(context.getResources().getString(R.string.app_name))
						.setContentText(context.getResources().getString(R.string.cast_notification_text))
						.addAction(new Action(at.huber.raspicast.R.drawable.ic_action_stop_dark,
								context.getResources().getString(R.string.stop), pi))
						.setColor(0xFF150C3F)
						.setDeleteIntent(pi)
						.setContentIntent(resultPendingIntent)
						.setPriority(NotificationCompat.PRIORITY_DEFAULT)
						.setOngoing(true);

		return mBuilder.build();
	}

	private void runCastCheckExecutor() {
		future = exec.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				if(!VideoControlExecutor.isRunning){
					SshConnection.readCastStatus(getApplicationContext());
				}
			}
		}, 5, 5, TimeUnit.SECONDS);
	}

	public static boolean isFileStreamServiceRunning(Context con) {
		ActivityManager manager = (ActivityManager) con.getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (FileStreamService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onDestroy(){
		if (future != null)
			future.cancel(true);

		if(wakelock!=null)
			wakelock.release();

		exec.shutdownNow();

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		// I don't need this.
		return null;
	}

}
