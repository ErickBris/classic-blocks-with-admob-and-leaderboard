package com.oldenweb.ClassicBlocks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetFileDescriptor;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.example.games.basegameutils.BaseGameActivity;

public class Main extends BaseGameActivity {
	final Handler h = new Handler();
	List<View> current_blocks;
	List<Integer> blocks_down;
	List<View> destroy_blocks;
	List<int[][]> figures;
	MediaPlayer mp;
	int score;
	int screen_width;
	int screen_height;
	SharedPreferences sp;
	Editor ed;
	boolean isForeground = true;
	SoundPool sndpool;
	int current_section = R.id.main;
	int snd_game_over;
	int snd_crush;
	int snd_fall;
	boolean show_leaderboard;
	int block_size;
	int speed;
	boolean game_paused;
	AnimatorSet anim;
	ViewGroup frame;
	PointF move_point = new PointF();
	int current_figure;
	int current_rotation;
	int next_figure = (int) Math.round(Math.random() * 6);
	int next_rotation = (int) Math.round(Math.random() * 3);
	final int fast_speed = 5; // speed when press down
	final int cols = 10; // number of columns
	final int rows = 20; // number of rows

	// AdMob
	AdView adMob_smart;
	InterstitialAd adMob_interstitial;
	final boolean show_admob_smart = true; // show AdMob Smart banner
	final boolean show_admob_interstitial = true; // show AdMob Interstitial

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// fullscreen
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// preferences
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		ed = sp.edit();

		// AdMob smart banner
		add_admob_smart();

		// bg sound
		mp = new MediaPlayer();
		try {
			AssetFileDescriptor descriptor = getAssets().openFd("snd_bg.mp3");
			mp.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
			descriptor.close();
			mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mp.setLooping(true);
			mp.setVolume(0, 0);
			mp.prepare();
			mp.start();
		} catch (Exception e) {
		}

		// if mute
		if (sp.getBoolean("mute", false))
			((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
		else
			mp.setVolume(0.5f, 0.5f);

		// block type
		((ToggleButton) findViewById(getResources().getIdentifier("btn_block" + sp.getInt("block_type", 0), "id",
				getPackageName()))).setChecked(true);

		// SoundPool
		sndpool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
		try {
			snd_game_over = sndpool.load(getAssets().openFd("snd_game_over.mp3"), 1);
			snd_crush = sndpool.load(getAssets().openFd("snd_crush.mp3"), 1);
			snd_fall = sndpool.load(getAssets().openFd("snd_fall.mp3"), 1);
		} catch (IOException e) {
		}

		// hide navigation bar listener
		findViewById(R.id.all).setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
				hide_navigation_bar();
			}
		});

		// controlstouch listener
		findViewById(R.id.btn_left).setOnTouchListener(controls_touch);
		findViewById(R.id.btn_right).setOnTouchListener(controls_touch);
		findViewById(R.id.btn_rotate).setOnTouchListener(controls_touch);

		// frame
		frame = (ViewGroup) findViewById(R.id.frame);

		// custom font
		Typeface font = Typeface.createFromAsset(getAssets(), "CooperBlack.otf");
		((TextView) findViewById(R.id.txt_result)).setTypeface(font);
		((TextView) findViewById(R.id.txt_high_result)).setTypeface(font);
		((TextView) findViewById(R.id.mess)).setTypeface(font);
		font = Typeface.createFromAsset(getAssets(), "BerlinSans.ttf");
		((TextView) findViewById(R.id.txt_score)).setTypeface(font);
		font = Typeface.createFromAsset(getAssets(), "BankGothic.ttf");
		((TextView) findViewById(R.id.txt_blocks)).setTypeface(font);

		SCALE();
	}

	// controls_touch
	OnTouchListener controls_touch = new OnTouchListener() {
		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: // down
				if (!game_paused && anim == null && findViewById(R.id.mess).getVisibility() == View.GONE) {
					switch (v.getId()) {
					case R.id.btn_left:
						// left
						v.setPressed(true);
						MOVE_LEFT.run();
						break;
					case R.id.btn_right:
						// right
						v.setPressed(true);
						MOVE_RIGHT.run();
						break;
					case R.id.btn_rotate:
						// rotate figure
						int target_rotation = current_rotation + 1;
						if (target_rotation > 3)
							target_rotation = 0;
						if (check_position(new PointF(move_point.x, move_point.y), target_rotation)) {
							current_rotation = target_rotation;
							change_position();
						}
						break;
					}
				}
				break;
			case MotionEvent.ACTION_UP: // up
			case MotionEvent.ACTION_CANCEL:
				switch (v.getId()) {
				case R.id.btn_left:
					h.removeCallbacks(MOVE_LEFT);
					break;
				case R.id.btn_right:
					h.removeCallbacks(MOVE_RIGHT);
					break;
				}
				break;
			}
			return false;
		}
	};

	// SCALE
	void SCALE() {
		// btn_play
		FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) findViewById(R.id.btn_play).getLayoutParams();
		l.width = (int) DpToPx(50);
		l.height = (int) DpToPx(50);
		l.setMargins(0, (int) DpToPx(7), (int) DpToPx(7), 0);
		findViewById(R.id.btn_play).setLayoutParams(l);

		// figure
		l = (FrameLayout.LayoutParams) findViewById(R.id.figure).getLayoutParams();
		l.width = (int) DpToPx(50);
		l.height = (int) DpToPx(50);
		l.setMargins((int) DpToPx(7), (int) DpToPx(7), 0, 0);
		findViewById(R.id.figure).setLayoutParams(l);

		// txt_score
		l = (FrameLayout.LayoutParams) findViewById(R.id.txt_score).getLayoutParams();
		l.height = (int) DpToPx(50);
		l.setMargins(0, (int) DpToPx(7), 0, 0);
		findViewById(R.id.txt_score).setLayoutParams(l);
		((TextView) findViewById(R.id.txt_score)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(34));

		// text mess
		((TextView) findViewById(R.id.mess)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(26));

		// text blocks
		((TextView) findViewById(R.id.txt_blocks)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(24));

		// buttons text
		((TextView) findViewById(R.id.btn_sign)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_leaderboard)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_sound)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_start)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_exit)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_home)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_start2)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));

		// text result
		((TextView) findViewById(R.id.txt_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(36));
		((TextView) findViewById(R.id.txt_high_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(16));
	}

	// START
	void START() {
		show_section(R.id.game);
		score = 0;
		speed = 300; // start speed
		anim = null;
		game_paused = false;
		((TextView) findViewById(R.id.txt_score)).setText(String.valueOf(score));
		findViewById(R.id.mess).setVisibility(View.GONE);
		((ToggleButton) findViewById(R.id.btn_play)).setChecked(true);
		frame.removeAllViews();

		// screen size
		screen_width = findViewById(R.id.all).getWidth();
		screen_height = findViewById(R.id.all).getHeight();

		// controls height
		findViewById(R.id.controls).getLayoutParams().height = screen_width / 4;

		// get block_size
		block_size = (int) Math.floor((screen_height - DpToPx(20) - findViewById(R.id.controls).getHeight()
				- findViewById(R.id.txt_score).getY() - findViewById(R.id.txt_score).getHeight())
				/ rows);

		// frame size and position
		frame.getLayoutParams().width = (int) (block_size * cols);
		frame.getLayoutParams().height = (int) (block_size * rows);
		frame.setY(findViewById(R.id.txt_score).getY()
				+ findViewById(R.id.txt_score).getHeight()
				+ (screen_height - findViewById(R.id.controls).getHeight() - findViewById(R.id.txt_score).getY()
						- findViewById(R.id.txt_score).getHeight() - frame.getHeight()) / 2f);

		// figures array
		figures = new ArrayList<int[][]>();

		// figure 1
		figures.add(new int[][] { { -block_size, -block_size }, { 0, -block_size }, { block_size, -block_size },
				{ 0, -block_size * 2 } });
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { 0, -block_size * 3 },
				{ block_size, -block_size * 2 } });
		figures.add(new int[][] { { 0, -block_size }, { -block_size, -block_size * 2 }, { 0, -block_size * 2 },
				{ block_size, -block_size * 2 } });
		figures.add(new int[][] { { 0, -block_size }, { -block_size, -block_size * 2 }, { 0, -block_size * 2 },
				{ 0, -block_size * 3 } });

		// figure 2
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { 0, -block_size * 3 }, { block_size, -block_size } });
		figures.add(new int[][] { { -block_size, -block_size }, { -block_size, -block_size * 2 }, { 0, -block_size * 2 },
				{ block_size, -block_size * 2 } });
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { 0, -block_size * 3 },
				{ -block_size, -block_size * 3 } });
		figures.add(new int[][] { { -block_size, -block_size }, { 0, -block_size }, { block_size, -block_size * 2 },
				{ block_size, -block_size } });

		// figure 3
		figures.add(new int[][] { { -block_size, -block_size }, { 0, -block_size * 2 }, { 0, -block_size * 3 },
				{ 0, -block_size } });
		figures.add(new int[][] { { -block_size, -block_size }, { -block_size, -block_size * 2 }, { 0, -block_size },
				{ block_size, -block_size } });
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { block_size, -block_size * 3 },
				{ 0, -block_size * 3 } });
		figures.add(new int[][] { { block_size, -block_size }, { -block_size, -block_size * 2 }, { 0, -block_size * 2 },
				{ block_size, -block_size * 2 } });

		// figure 4
		figures.add(new int[][] { { 0, -block_size }, { block_size, -block_size }, { 0, -block_size * 2 },
				{ block_size, -block_size * 2 } });
		figures.add(new int[][] { { 0, -block_size }, { block_size, -block_size }, { 0, -block_size * 2 },
				{ block_size, -block_size * 2 } });
		figures.add(new int[][] { { 0, -block_size }, { block_size, -block_size }, { 0, -block_size * 2 },
				{ block_size, -block_size * 2 } });
		figures.add(new int[][] { { 0, -block_size }, { block_size, -block_size }, { 0, -block_size * 2 },
				{ block_size, -block_size * 2 } });

		// figure 5
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { 0, -block_size * 3 }, { 0, -block_size * 4 } });
		figures.add(new int[][] { { -block_size, -block_size }, { 0, -block_size }, { block_size, -block_size },
				{ block_size * 2, -block_size } });
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { 0, -block_size * 3 }, { 0, -block_size * 4 } });
		figures.add(new int[][] { { -block_size, -block_size }, { 0, -block_size }, { block_size, -block_size },
				{ block_size * 2, -block_size } });

		// figure 6
		figures.add(new int[][] { { -block_size, -block_size * 2 }, { 0, -block_size * 2 }, { 0, -block_size },
				{ block_size, -block_size } });
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { block_size, -block_size * 2 },
				{ block_size, -block_size * 3 } });
		figures.add(new int[][] { { -block_size, -block_size * 2 }, { 0, -block_size * 2 }, { 0, -block_size },
				{ block_size, -block_size } });
		figures.add(new int[][] { { 0, -block_size }, { 0, -block_size * 2 }, { block_size, -block_size * 2 },
				{ block_size, -block_size * 3 } });

		// figure 7
		figures.add(new int[][] { { block_size, -block_size * 2 }, { block_size * 2, -block_size * 2 }, { 0, -block_size },
				{ block_size, -block_size } });
		figures.add(new int[][] { { 0, -block_size * 3 }, { 0, -block_size * 2 }, { block_size, -block_size * 2 },
				{ block_size, -block_size } });
		figures.add(new int[][] { { block_size, -block_size * 2 }, { block_size * 2, -block_size * 2 }, { 0, -block_size },
				{ block_size, -block_size } });
		figures.add(new int[][] { { 0, -block_size * 3 }, { 0, -block_size * 2 }, { block_size, -block_size * 2 },
				{ block_size, -block_size } });

		// add figure
		add_figure();

		// move
		h.postDelayed(MOVE, speed);
	}

	// add_figure
	void add_figure() {
		// random figure
		current_figure = next_figure;
		current_rotation = next_rotation;

		// move_point
		if (current_figure == 2 && current_rotation == 2)
			move_point.x = Math.round((cols - 3) / 2f) * block_size;
		else if (current_figure == 2 && current_rotation == 0)
			move_point.x = Math.round((cols) / 2f) * block_size;
		else if (current_figure == 1 && current_rotation == 2)
			move_point.x = Math.round((cols - 1) / 2f) * block_size;
		else
			move_point.x = Math.round((cols - 2) / 2f) * block_size;
		move_point.y = 0;

		// add blocks
		current_blocks = new ArrayList<View>();
		for (int i = 0; i < 4; i++) {
			View block = new View(this);
			block.setLayoutParams(new LayoutParams(block_size, block_size));
			block.setBackgroundResource(getResources().getIdentifier("block_type" + sp.getInt("block_type", 0), "drawable",
					getPackageName()));
			block.setX(move_point.x + figures.get(current_figure * 4 + current_rotation)[i][0]);
			block.setY(move_point.y + figures.get(current_figure * 4 + current_rotation)[i][1]);
			frame.addView(block);
			current_blocks.add(block);
		}

		// next figure
		next_figure = (int) Math.round(Math.random() * 6);
		next_rotation = (int) Math.round(Math.random() * 3);

		((ImageView) findViewById(R.id.figure)).setImageResource(getResources().getIdentifier(
				"figure" + (next_figure * 4 + next_rotation), "drawable", getPackageName()));
	}

	// check_position
	boolean check_position(PointF target_point, int target_rotation) {
		// hit with other blocks
		for (int i = 0; i < frame.getChildCount(); i++)
			if (current_blocks.indexOf(frame.getChildAt(i)) == -1)
				for (int j = 0; j < current_blocks.size(); j++)
					if (new RectF(frame.getChildAt(i).getX(), frame.getChildAt(i).getY(),
							frame.getChildAt(i).getX() + block_size, frame.getChildAt(i).getY() + block_size)
							.intersect(new RectF(target_point.x + block_size / 2f
									+ figures.get(current_figure * 4 + target_rotation)[j][0], target_point.y + block_size / 2f
									+ figures.get(current_figure * 4 + target_rotation)[j][1], target_point.x + block_size / 2f
									+ figures.get(current_figure * 4 + target_rotation)[j][0], target_point.y + block_size / 2f
									+ figures.get(current_figure * 4 + target_rotation)[j][1])))
						return false;

		// offside
		for (int i = 0; i < current_blocks.size(); i++)
			if (target_point.x + block_size / 2f + figures.get(current_figure * 4 + target_rotation)[i][0] < 0
					|| target_point.x + block_size / 2f + figures.get(current_figure * 4 + target_rotation)[i][0] >= frame
							.getWidth()
					|| target_point.y + figures.get(current_figure * 4 + target_rotation)[i][1] >= frame.getHeight())
				return false;

		return true;
	}

	// change_position
	void change_position() {
		for (int i = 0; i < current_blocks.size(); i++) {
			current_blocks.get(i).setX(move_point.x + figures.get(current_figure * 4 + current_rotation)[i][0]);
			current_blocks.get(i).setY(move_point.y + figures.get(current_figure * 4 + current_rotation)[i][1]);
		}
	}

	// MOVE
	Runnable MOVE = new Runnable() {
		@Override
		public void run() {
			blocks_down = new ArrayList<Integer>();
			destroy_blocks = new ArrayList<View>();

			// move down figure
			if (check_position(new PointF(move_point.x, move_point.y + block_size), current_rotation)) {
				move_point.y += block_size;
				change_position();

				// next move timeout
				h.postDelayed(MOVE, (findViewById(R.id.btn_down).isPressed() && anim == null) ? 5 : speed);
			} else {
				// firure fall sound
				if (findViewById(R.id.btn_down).isPressed() && anim == null && !sp.getBoolean("mute", false) && isForeground)
					sndpool.play(snd_fall, 1f, 1f, 0, 0, 1);

				// game over
				for (int i = 0; i < current_blocks.size(); i++)
					if (current_blocks.get(i).getY() <= 0) {
						h.removeCallbacks(MOVE_LEFT);
						h.removeCallbacks(MOVE_RIGHT);
						findViewById(R.id.mess).setVisibility(View.VISIBLE);

						// sound
						if (!sp.getBoolean("mute", false) && isForeground)
							sndpool.play(snd_game_over, 0.7f, 0.7f, 0, 0, 1);

						h.postDelayed(STOP, 3000);
						return;
					}

				// blocks_down
				for (int i = 0; i < frame.getChildCount(); i++)
					blocks_down.add(0);

				// check destroy lines
				int destroy_lines = 0;
				for (int j = rows; j > 0; j--) {
					// blocks on line
					List<View> line_blocks = new ArrayList<View>();
					for (int i = 0; i < cols; i++)
						for (int n = 0; n < frame.getChildCount(); n++)
							if (destroy_blocks.indexOf(frame.getChildAt(n)) == -1
									&& new RectF(frame.getChildAt(n).getX(), frame.getChildAt(n).getY(), frame.getChildAt(n)
											.getX() + block_size, frame.getChildAt(n).getY() + block_size).intersect(new RectF(i
											* block_size + block_size / 2f, j * block_size - block_size / 2f, i * block_size
											+ block_size / 2f, j * block_size - block_size / 2f)))
								line_blocks.add(frame.getChildAt(n));

					// full line
					if (line_blocks.size() == cols) {
						destroy_lines++;

						// destroy_blocks
						for (int i = 0; i < line_blocks.size(); i++)
							destroy_blocks.add(line_blocks.get(i));

						// blocks_down
						for (int i = 0; i < frame.getChildCount(); i++) {
							if (destroy_blocks.indexOf(frame.getChildAt(i)) == -1
									&& frame.getChildAt(i).getY() < j * block_size - block_size / 2f - block_size)
								blocks_down.set(i, destroy_lines);
						}
					}
				}

				// glow blocks
				if (destroy_blocks.size() != 0) {
					// score;
					switch (destroy_lines) {
					case 1:
						score = score + 10;
						break;
					case 2:
						score = score + 30;
						break;
					case 3:
						score = score + 50;
						break;
					case 4:
						score = score + 70;
						break;
					}
					((TextView) findViewById(R.id.txt_score)).setText(String.valueOf(score));

					// sound crush
					if (!sp.getBoolean("mute", false) && isForeground)
						sndpool.play(snd_crush, 1f, 1f, 0, 0, 1);

					speed = Math.max(speed - 2, 150); // speed up
					show_animation();
				} else {
					// next figure
					add_figure();
					h.postDelayed(MOVE, speed);
				}
			}
		}
	};

	// show_animation
	void show_animation() {
		// AnimatorSet
		List<Animator> anim_list = new ArrayList<Animator>();

		// create animation
		for (int i = 0; i < destroy_blocks.size(); i++) {
			ObjectAnimator anim = ObjectAnimator.ofFloat(destroy_blocks.get(i), "alpha", 0.5f);
			anim.setRepeatCount(4);
			anim.setRepeatMode(ObjectAnimator.REVERSE);
			anim_list.add(anim);
		}

		// animate
		anim = new AnimatorSet();
		anim.playTogether(anim_list);
		anim.setDuration(100);
		anim.addListener(new AnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {
			}

			@Override
			public void onAnimationRepeat(Animator animation) {
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				// blocks_down
				for (int i = 0; i < blocks_down.size(); i++) {
					frame.getChildAt(i).setY(frame.getChildAt(i).getY() + blocks_down.get(i) * block_size);
				}

				// remove blocks
				for (int i = 0; i < destroy_blocks.size(); i++) {
					frame.removeView(destroy_blocks.get(i));
				}

				// next figure
				add_figure();
				anim = null;
				h.postDelayed(MOVE, speed);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
			}
		});
		anim.start();
	}

	// MOVE_LEFT
	Runnable MOVE_LEFT = new Runnable() {
		@Override
		public void run() {
			if (!game_paused && anim == null && findViewById(R.id.mess).getVisibility() == View.GONE
					&& findViewById(R.id.btn_left).isPressed()
					&& check_position(new PointF(move_point.x - block_size, move_point.y), current_rotation)) {
				move_point.x -= block_size;
				change_position();
				h.postDelayed(MOVE_LEFT, 100);
			}
		}
	};

	// MOVE_RIGHT
	Runnable MOVE_RIGHT = new Runnable() {
		@Override
		public void run() {
			if (!game_paused && anim == null && findViewById(R.id.mess).getVisibility() == View.GONE
					&& findViewById(R.id.btn_right).isPressed()
					&& check_position(new PointF(move_point.x + block_size, move_point.y), current_rotation)) {
				move_point.x += block_size;
				change_position();
				h.postDelayed(MOVE_RIGHT, 100);
			}
		}
	};

	// STOP
	Runnable STOP = new Runnable() {
		@Override
		public void run() {
			show_section(R.id.result);
			h.removeCallbacks(MOVE);
			h.removeCallbacks(STOP);
			h.removeCallbacks(MOVE_LEFT);
			h.removeCallbacks(MOVE_RIGHT);

			// save score
			if (score > sp.getInt("score", 0)) {
				ed.putInt("score", score);
				ed.commit();
			}

			// show score
			((TextView) findViewById(R.id.txt_result)).setText(getString(R.string.score) + " " + score);
			((TextView) findViewById(R.id.txt_high_result)).setText(getString(R.string.high_score) + " " + sp.getInt("score", 0));

			// save score to leaderboard
			if (getApiClient().isConnected())
				Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_id), sp.getInt("score", 0));

			// AdMob Interstitial
			add_admob_interstitial();
		}
	};

	// onClick
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_start:
		case R.id.btn_start2:
			START();
			break;
		case R.id.btn_exit:
			finish();
			break;
		case R.id.btn_sound:
			if (sp.getBoolean("mute", false)) {
				ed.putBoolean("mute", false);
				mp.setVolume(0.5f, 0.5f);
				((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_mute));
			} else {
				ed.putBoolean("mute", true);
				mp.setVolume(0, 0);
				((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
			}
			ed.commit();
			break;
		case R.id.btn_play:
			if (((ToggleButton) v).isChecked()) {
				game_paused = false;
				h.postDelayed(MOVE, speed);
			} else {
				game_paused = true;
				if (anim != null && anim.isRunning())
					anim.end();
				h.removeCallbacks(MOVE);
			}
			break;
		case R.id.btn_leaderboard:
			// show leaderboard
			show_leaderboard = true;
			if (getApiClient().isConnected())
				onSignInSucceeded();
			else
				beginUserInitiatedSignIn();
			break;
		case R.id.btn_sign:
			// Google sign in/out
			if (getApiClient().isConnected()) {
				signOut();
				onSignInFailed();
			} else
				beginUserInitiatedSignIn();
			break;
		case R.id.btn_block0:
		case R.id.btn_block1:
		case R.id.btn_block2:
		case R.id.btn_block3:
		case R.id.btn_block4:
			((ToggleButton) findViewById(R.id.btn_block0)).setChecked(false);
			((ToggleButton) findViewById(R.id.btn_block1)).setChecked(false);
			((ToggleButton) findViewById(R.id.btn_block2)).setChecked(false);
			((ToggleButton) findViewById(R.id.btn_block3)).setChecked(false);
			((ToggleButton) findViewById(R.id.btn_block4)).setChecked(false);
			((ToggleButton) v).setChecked(true);
			ed.putInt("block_type", Integer.valueOf(v.getTag().toString()));
			ed.commit();
			break;
		case R.id.btn_home:
			show_section(R.id.main);
			break;
		}
	}

	@Override
	public void onBackPressed() {
		switch (current_section) {
		case R.id.main:
			super.onBackPressed();
			break;
		case R.id.result:
			show_section(R.id.main);
			break;
		case R.id.game:
			show_section(R.id.main);
			h.removeCallbacks(MOVE);
			h.removeCallbacks(STOP);
			h.removeCallbacks(MOVE_LEFT);
			h.removeCallbacks(MOVE_RIGHT);

			if (anim != null)
				anim.cancel();
			break;
		}
	}

	// show_section
	void show_section(int section) {
		current_section = section;
		findViewById(R.id.main).setVisibility(View.GONE);
		findViewById(R.id.game).setVisibility(View.GONE);
		findViewById(R.id.result).setVisibility(View.GONE);
		findViewById(current_section).setVisibility(View.VISIBLE);
	}

	@Override
	protected void onDestroy() {
		h.removeCallbacks(MOVE);
		h.removeCallbacks(STOP);
		mp.release();
		sndpool.release();

		if (anim != null)
			anim.cancel();

		// destroy AdMob
		if (adMob_smart != null)
			adMob_smart.destroy();

		super.onDestroy();
	}

	@Override
	protected void onPause() {
		isForeground = false;
		mp.setVolume(0, 0);
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		isForeground = true;

		// music
		if (!sp.getBoolean("mute", false) && isForeground)
			mp.setVolume(0.5f, 0.5f);
	}

	// DpToPx
	float DpToPx(float dp) {
		return (dp * Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) / 520f);
	}

	// hide_navigation_bar
	@TargetApi(Build.VERSION_CODES.KITKAT)
	void hide_navigation_bar() {
		// fullscreen mode
		if (android.os.Build.VERSION.SDK_INT >= 19) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			hide_navigation_bar();
	}

	@Override
	public void onSignInSucceeded() {
		((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_out));

		// save score to leaderboard
		if (show_leaderboard) {
			Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_id), sp.getInt("score", 0));

			// show leaderboard
			startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), getString(R.string.leaderboard_id)),
					9999);
		}

		// get score from leaderboard
		Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(), getString(R.string.leaderboard_id),
				LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC).setResultCallback(
				new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
					@Override
					public void onResult(final Leaderboards.LoadPlayerScoreResult scoreResult) {
						if (scoreResult != null && scoreResult.getStatus().getStatusCode() == GamesStatusCodes.STATUS_OK
								&& scoreResult.getScore() != null) {
							// save score localy
							if ((int) scoreResult.getScore().getRawScore() > sp.getInt("score", 0)) {
								ed.putInt("score", (int) scoreResult.getScore().getRawScore());
								ed.commit();
							}
						}
					}
				});

		show_leaderboard = false;
	}

	@Override
	public void onSignInFailed() {
		((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_in));
		show_leaderboard = false;
	}

	// add_admob_smart
	void add_admob_smart() {
		if (show_admob_smart
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_smart = new AdView(this);
			adMob_smart.setAdUnitId(getString(R.string.adMob_smart));
			adMob_smart.setAdSize(AdSize.SMART_BANNER);
			((ViewGroup) findViewById(R.id.admob)).addView(adMob_smart);
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_smart.loadAd(builder.build());
		}
	}

	// add_admob_interstitial
	void add_admob_interstitial() {
		if (show_admob_interstitial
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_interstitial = new InterstitialAd(this);
			adMob_interstitial.setAdUnitId(getString(R.string.adMob_interstitial));
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_interstitial.setAdListener(new AdListener() {
				@Override
				public void onAdLoaded() {
					super.onAdLoaded();

					if (current_section != R.id.game)
						adMob_interstitial.show();
				}
			});
			adMob_interstitial.loadAd(builder.build());
		}
	}
}