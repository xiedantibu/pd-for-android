/**
 * 
 * @author Martin Roth (mhroth@rjdj.me)
 * @author Peter Brinkmann (peter.brinkmann@gmail.com)
 * 
 * For information on usage and redistribution, and for a DISCLAIMER OF ALL
 * WARRANTIES, see the file, "LICENSE.txt," in this distribution.
 * 
 */

package org.puredata.android.scenes;

import java.io.File;

import org.puredata.android.scenes.SceneDataBase.RecordingColumn;
import org.puredata.android.scenes.SceneDataBase.SceneColumn;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class RecordingPlayer extends Activity implements OnSeekBarChangeListener, OnCheckedChangeListener, OnClickListener {

	private final static String TAG = "RecordingPlayer";
	private SceneDataBase db;
	private long recordingId;
	private long sceneId;
	private ToggleButton playButton;
	private ImageButton sceneButton;
	private ImageButton editButton;
	private SeekBar seekBar;
	private MediaPlayer mediaPlayer;
	private boolean playbackState;
	private Thread updateThread = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.recording_player);
		recordingId = this.getIntent().getLongExtra(RecordingColumn.ID.getLabel(), -1);
		if (recordingId >= 0) {
			db = new SceneDataBase(this);
			Cursor cursor = db.getRecording(recordingId);
			sceneId = SceneDataBase.getLong(cursor, RecordingColumn.SCENE_ID);
			displaySceneInfo(sceneId);
			seekBar = (SeekBar) findViewById(R.id.recording_play_seekbar);
			seekBar.setMax((int) SceneDataBase.getLong(cursor, RecordingColumn.RECORDING_DURATION));
			seekBar.setOnSeekBarChangeListener(this);
			TextView textView = (TextView) findViewById(R.id.recording_play_description_text);
			textView.setText(SceneDataBase.getString(cursor, RecordingColumn.RECORDING_DESCRIPTION));
			textView = (TextView) findViewById(R.id.recording_play_duration_text);
			String durationFormat = this.getString(R.string.duration_format);
			textView.setText(DateFormat.format(durationFormat, SceneDataBase.getLong(cursor, RecordingColumn.RECORDING_DURATION)));
			textView = (TextView) findViewById(R.id.recording_play_date_text);
			String dateFormat = this.getString(R.string.date_format);
			textView.setText(DateFormat.format(dateFormat, SceneDataBase.getLong(cursor, RecordingColumn.RECORDING_TIMESTAMP)));
			playButton = (ToggleButton) findViewById(R.id.recording_play_play_button);
			playButton.setOnCheckedChangeListener(this);
			playButton.setChecked(true);
			sceneButton = (ImageButton) findViewById(R.id.recording_play_scene_button);
			sceneButton.setOnClickListener(this);
			editButton = (ImageButton) findViewById(R.id.recording_play_rename_button);
			editButton.setOnClickListener(this);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setLooping(true);
			String recPath = SceneDataBase.getString(cursor, RecordingColumn.RECORDING_PATH);
			try {
				mediaPlayer.setDataSource(recPath);
				mediaPlayer.prepare();
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				finish();
			}
		} else {
			Log.i(TAG, "invalid recording ID");
			finish();
		}
	}
	
	private void displaySceneInfo(long sceneId) {
		Cursor cursor = db.getScene(sceneId);
		if (cursor.getCount() > 0) {
			TextView textView = (TextView) findViewById(R.id.recording_play_title);
			textView.setText(SceneDataBase.getString(cursor, SceneColumn.SCENE_TITLE));
			textView = (TextView) findViewById(R.id.recording_play_artist);
			textView.setText(SceneDataBase.getString(cursor, SceneColumn.SCENE_ARTIST));
			String scenePath = SceneDataBase.getString(cursor, SceneColumn.SCENE_DIRECTORY);
			ImageView imageView = (ImageView) findViewById(R.id.recording_play_image);
			imageView.setImageBitmap(BitmapFactory.decodeFile(new File(scenePath, "image.jpg").getAbsolutePath()));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (playButton.isChecked()) {
			startPlayback();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		stopPlayback();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mediaPlayer.release();
	}

	private void startPlayback() {
		startUpdateThread();
		mediaPlayer.start();
	}

	private void stopPlayback() {
		mediaPlayer.pause();
		stopUpdateThread();
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			mediaPlayer.seekTo(progress);
		}
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
		playbackState = mediaPlayer.isPlaying();
		stopPlayback();
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
		if (playbackState) {
			startPlayback();
		}
	}
	
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (isChecked) {
			startPlayback();
		} else {
			stopPlayback();
		}
	}

	public void onClick(View v) {
		if (v.equals(sceneButton)) {
			Intent intent = new Intent(RecordingPlayer.this, ScenePlayer.class);
			intent.putExtra(SceneColumn.ID.getLabel(), sceneId);
			startActivity(intent);
		} else if (v.equals(editButton)) {
			// edit...
		}
	}
	
	private void startUpdateThread() {
		stopUpdateThread();
		updateThread = new Thread() {
			@Override
			public void run() {
				while (!isInterrupted()) {
					seekBar.setProgress(mediaPlayer.getCurrentPosition());
					try {
						Thread.sleep(250);
					} catch (InterruptedException e) {
						break;
					}
				}
			};
		};
		updateThread.start();
	}
	
	private void stopUpdateThread() {
		if (updateThread != null) {
			updateThread.interrupt();
			try {
				updateThread.join();
			} catch (InterruptedException e) {
				// Don't care.
			}
			updateThread = null;
		}
	}
}
