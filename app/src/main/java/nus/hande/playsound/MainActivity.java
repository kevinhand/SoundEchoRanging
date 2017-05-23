package nus.hande.playsound;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;

import nus.hande.playsound.AudioProcess.MyBinder;



public class MainActivity extends Activity {
	private final double duration = 0.002; // seconds
    private final int sampleRate = 44100;
    private final int numSamples = (int)( duration * sampleRate);//88
    private final double sample[] = new double[numSamples];
    private  double freqOfTone = 18000; // hz
    private int numberOfFrequency = 20;
 
    private TextView Textbox;
    private final byte generatedSnd[] = new byte[2 * numSamples];
    private AudioProcess maudioProcess = null;//处理
	private Intent mIntent = null;
	boolean FirstTime = true;
	private LineChart mChart;

    Handler handler = new Handler();
	IntentFilter intentFilter = new IntentFilter(
			"android.intent.action.MAIN");
	private BroadcastReceiver mReceiver;

//	AudioTrack audioTrack = null;

	Button buttonPressed;
	Button buttonUnpressed;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);	
		Textbox = (TextView)findViewById(R.id.result);
		Textbox.setMovementMethod(new ScrollingMovementMethod());

		buttonPressed = (Button) findViewById(R.id.circlebuttonpressed);
		buttonUnpressed = (Button) findViewById(R.id.circlebuttonunpress);
		
		if(maudioProcess == null)
    	{
	    	mIntent = new Intent(MainActivity.this, AudioProcess.class);
			//startService(mIntent);
	        
	    	this.getApplicationContext().bindService(mIntent, conn, Context.BIND_AUTO_CREATE);
	    	
//    		Intent intent = new Intent();
//    		intent.setAction("com.example.steptracking.TrackingService");  
//            startService(intent);  
    	}
		mChart = (LineChart) findViewById(R.id.chart);
//		maudioProcess.start(freqOfTone1, freqOfTone2,freqOfTone3);// Double.parseDouble(freqField.getText().toString()));

//		genTone();
//		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
//                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
//                AudioTrack.MODE_STATIC);
//        audioTrack.write(generatedSnd, 0, generatedSnd.length);
		
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
//	public void startButtonOnclick(View view){
//			if(FirstTime){
//				maudioProcess.measureStart();
//				FirstTime = false;
//			}
//			 genTone();
//			 maudioProcess.pressButton();
//	        Thread thread = new Thread(new Runnable() {
//	            public void run() {
//	                handler.post(new Runnable() {
//	                    public void run() {
//	                    	  playSound();
//	                    }
//	                });
//	            }
//	        });
//	        thread.start();
//	        thread =null;
//	}
int executionTick = 20;
	public void ListenButtonOnclick(final View view){

		buttonUnpressed.setEnabled(false);
		buttonUnpressed.setVisibility(view.INVISIBLE);
		buttonPressed.setEnabled(true);
		buttonPressed.setVisibility(view.VISIBLE);

		if(FirstTime){
			maudioProcess.measureStart();
			FirstTime = false;
		}
//		else{
//			maudioProcess.measureStop();
//			maudioProcess.measureStart();
//		}
		genTone();
		maudioProcess.prepareCounter();
		executionTick = 20;
        Thread thread = new Thread(new Runnable() {
            public void run() {       
                handler.post(new Runnable() {              	
                    public void run() {
//						while(true){
//							if(executionTick==0)
//								break;
							playSound();
							executionTick--;
//						}


                    }
                });
            }
        });
        thread.start();
		thread = null;
        
		mReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				//extract our message from intent
				 genTone();
		        Thread thread = new Thread(new Runnable() {
		            public void run() {
		                handler.post(new Runnable() {
		                    public void run() {
//								maudioProcess.measureStop();
//								maudioProcess.measureStart();
								playSound();
								executionTick--;
								if(executionTick<1){
									StopListenButtonOnclick(view);
								}

		                    }
		                });
		            }
		        });
		        thread.start();
				thread = null;

			}
		};
		this.registerReceiver(mReceiver, intentFilter);
	}

	public void StopListenButtonOnclick(View view){

		buttonUnpressed.setEnabled(true);
		buttonUnpressed.setVisibility(view.VISIBLE);
		buttonPressed.setEnabled(false);
		buttonPressed.setVisibility(view.INVISIBLE);
//		maudioProcess.resetCounter();
//		audioTrack.release();
//		audioTrack = null;
		if(this.mReceiver!=null) {
			this.unregisterReceiver(this.mReceiver);
			this.mReceiver = null;
		}
//		maudioProcess.measureStop();
	}

    @Override
    protected void onResume() {
        super.onResume();

        /*// Use a new tread as this can take a while
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                genTone();
                handler.post(new Runnable() {

                    public void run() {
                        playSound();
                       
                    }
                });
            }
        });
        thread.start();*/
    }

    void genTone(){
    	
    	//freqOfTone = 11050;//Double.parseDouble(freqField.getText().toString());
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
        	for(int j=0;j<numberOfFrequency;j++){
        		sample[i]+=Math.sin(2 * Math.PI * i / (sampleRate/(freqOfTone+150*j)));
        	}
        	sample[i]=sample[i]/numberOfFrequency;
        }    
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32700));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }

    void playSound(){
		final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, generatedSnd.length);

		if(audioTrack.getState() ==  AudioTrack.STATE_INITIALIZED){
			audioTrack.play();
			new Thread(new Runnable(){
				@Override
				public void run(){
					try {
						Thread.sleep(500);
						audioTrack.release();
//						audioTrack =null;
					} catch (InterruptedException e) { e.printStackTrace(); }
				}
			}).start();
		}else{
//			audioTrack.stop();
//			audioTrack.release();
//			audioTrack =null;
		}
//		try {
//			Thread.sleep(3000);
//		} catch (InterruptedException e) { e.printStackTrace(); }
    }
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
		if(this.mReceiver!=null) {
			this.unregisterReceiver(this.mReceiver);
			this.mReceiver = null;
		}
		maudioProcess.stop();
		if(maudioProcess!=null)
    	{
			maudioProcess.stop();
    		this.getApplicationContext().unbindService(conn);
    	}
		maudioProcess = null;

	}
    
private ServiceConnection conn = new ServiceConnection() {
        
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
        	maudioProcess.onDestroy();
        }
        
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO Auto-generated method stub
            MyBinder binder = (MyBinder)service;
            maudioProcess = binder.getService();
            maudioProcess.setElement(Textbox, mChart);
            maudioProcess.start(freqOfTone, numberOfFrequency);// Double.parseDouble(freqField.getText().toString()));         
        }
    };
   

}
