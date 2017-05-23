package nus.hande.playsound;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.Utils;

import org.hermit.dsp.FFTTransformer;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;


public class AudioProcess extends Service{
	public static final float pi= (float)Math.PI;
	static final int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;  
	static final int audioEncodeing = AudioFormat.ENCODING_PCM_16BIT; 
	int bufferSizeInBytes;//采集数据需要的缓冲区大小
	AudioRecord audioRecord;//录音
	String filename ;
	// Fourier Transform calculator we use for calculating the spectrum
	// and sonagram.
	private FFTTransformer spectrumAnalyser;
	private LineChart mChart;
		
	// Analyzed audio spectrum data; history data for each frequency
	// in the spectrum; index into the history data
	private float[] spectrumData;
	private float[][] spectrumHist;
	private int spectrumIndex;
	
	// The desired histogram averaging window. 1 means no averaging.
	private int historyLen = 3;
	
	// Buffered audio data, and sequence number of the latest block.
	private short[] audioData;
	private long audioSequence = 0;

	// If we got a read error, the error code.
	private int readError = AudioReader.Listener.ERR_OK;

	// Sequence number of the last block we processed.
	private long audioProcessed = 0;
	
	// Our audio input device.
	private AudioReader audioReader;

	public final static int AUDIO_SAMPLE_RATE = 44100;  //44.1KHz,普遍使用的频率   
	private  double  mfrequency;
	private  int numOfFrequency;
	int logcounter=0;
	private TextView mresult;
	private Handler mHandler = new Handler();

	private int inputBlockSize =22; //accuracy 8cm
	private static DataOutputStream logWriter=null;
	int runcounter =0;
	boolean FirstPeakDetected = false;

	private int HistLength ;
	private int SampleToPass;
	
	private double[] HistData1; 
	private int HistIndex1 =0;
	private int TimeCount;
	private PeakDetect mPeakDetect = null;
	public Object mlock ;

	
	//启动程序
	public void start( double frequency, int NOF) {
		
		audioReader = new AudioReader();
//		TimeCount = 0;
		//spectrumAnalyser = new FFTTransformer(inputBlockSize, windowFunction);
		// Allocate the spectrum data.
		spectrumData = new float[inputBlockSize / 2];
		spectrumHist = new float[inputBlockSize / 2][historyLen];
		spectrumIndex = 0;
		HistLength = 20*44/inputBlockSize;
		SampleToPass = 3*44/(inputBlockSize);
		
		HistData1 = new double[HistLength];
		
		mfrequency = frequency;
		numOfFrequency = NOF;

//		new RecordThread(audioRecord, bufferSizeInBytes,mresult).start();
	}
	
	/**
	 * We are starting the main run; start measurements.
	 */

	public void LogCreate(){
		if(logWriter == null){
			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			File folder= new File(path+"/SoundLog/");
	    	if (!folder.exists()) {
	    		folder.mkdirs();
	    	}		    	
	    	logcounter++;
	    	File f = new File(path+"/SoundLog/Sound"+logcounter+".txt");

	    	while(f.exists()){
	    		logcounter++;
	    		f = new File(path+"/SoundLog/Sound"+logcounter+".txt");
	    	}	    	
	    	
	    	try {
				f.createNewFile();
				FileOutputStream fos = new FileOutputStream(f);
				logWriter = new DataOutputStream(fos);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void measureStart() {

	//	audioReader = new AudioReader();
//		LogCreate();
		//audioReader = new AudioReader();
		audioProcessed = audioSequence = 0;
		readError = AudioReader.Listener.ERR_OK;
		audioReader.startReader(AUDIO_SAMPLE_RATE, inputBlockSize,
				new AudioReader.Listener() {
					@Override
					public final void onReadComplete(short[] buffer) {
						receiveAudio(buffer);
						doUpdate();
					}

					@Override
					public void onReadError(int error) {
						handleError(error);
					}
				});
		
	}
	
	public void ResetCounter(){
		runcounter =0;
	}
	
	/**
	 * We are stopping / pausing the run; stop measurements.
	 */
	public void measureStop() {
		audioReader.stopReader();
//		try {
//			logWriter.flush();
//			logWriter.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	/**
	 * Handle audio input. This is called on the thread of the audio reader.
	 * 
	 * @param buffer
	 *            Audio data that was just read.
	 */
	private final void receiveAudio(short[] buffer) {
		// Lock to protect updates to these local variables. See run().
		synchronized (this) {
			audioData = buffer;
			++audioSequence;
		}
	}
	
	/**
	 * An error has occurred. The reader has been terminated.
	 * 
	 * @param error
	 *            ERR_XXX code describing the error.
	 */
	private void handleError(int error) {
		synchronized (this) {
			readError = error;
		}
	}
	
	public void doUpdate() {
			short[] buffer = null;
			synchronized (this) {
				if (audioData != null && audioSequence > audioProcessed) {				
					audioProcessed = audioSequence;
					buffer = audioData;
				}
			}

			// If we got data, process it without the lock.
			if (buffer != null && buffer.length ==inputBlockSize )
				processAudio(buffer);
		
	}
	
	
	/**
	 * Handle audio input. This is called on the thread of the parent surface.
	 * 
	 * @param buffer
	 *            Audio data that was just read.
	 */
	int tick =-1;
	double[] magnitude =new double[]{1,1,1};
	Window Window =null;
	double SingleValue = 0;
	
	private final void processAudio(short[] buffer){
		short[]tmpBuf = new short[inputBlockSize];	
		int[]inputBuf = new int[inputBlockSize];	
		synchronized (buffer) {
			final int len = buffer.length;
			System.arraycopy(buffer, 0, tmpBuf, len-inputBlockSize, inputBlockSize);
			// Tell the reader we're done with the buffer.
			buffer.notify();
		}
		
		for(int i=0;i < inputBlockSize; i++){
			int tmpint = tmpBuf[i];
			inputBuf[i] = tmpint;
	}

		
		tick++;
		
		for(int i = 0;i<numOfFrequency;i++){
			SingleValue += goertzelFilter(inputBuf, mfrequency+150*i,inputBlockSize);
		}
		SingleValue=SingleValue/numOfFrequency;
		
		if(tick <=2){
			magnitude [tick] = SingleValue;
		}
		if(tick ==2){
			Window = new Window(magnitude);
		}
		if(tick >2)
		{
			Window.Forward(SingleValue);		
			PrepareDataForPeakDetection(Window);
		}
}
	
	int datacounter;
	int imagedataIndex =0;
	private double[] imageData;
	private void PrepareDataForPeakDetection(Window magnitude ){

		if(FirstPeakDetected){
			imageData[++imagedataIndex] = magnitude.getLast();
			if(imagedataIndex>SampleToPass){
				HistData1 [datacounter++] = magnitude.getLast();//*magnitude.getLast();
			}
			
		}
		//make sure get the right first peak
		if(Math.log10(magnitude.data[1]) >= 7.5 && !FirstPeakDetected && TimeCount>0 && (magnitude.data[0]<magnitude.data[1]) && (magnitude.data[2]<magnitude.data[1])){
			FirstPeakDetected = true;
			imagedataIndex =0;
			imageData= new double[HistLength+1];
			imageData[imagedataIndex] = magnitude.data[1];
			datacounter=0;
			HistData1 = new double[HistLength-SampleToPass];
			TimeCount--;
		}
		
		try{
			if(datacounter >=HistLength-SampleToPass){
				mPeakDetect = new PeakDetect(HistData1);
				int[] peakIndex = mPeakDetect.process(4, 1);
				if(peakIndex.length !=0) {
					String listofpeak = "";
					double lastPeak = Math.log10(imageData[0]);
					boolean IsGoodTrend = true;
					for( int peak =0;peak<peakIndex.length;peak++){
						double Apeak =  round(0.33*inputBlockSize*(peakIndex[peak]+SampleToPass+2)/2/44,2);
						double peakValue = Math.log10(HistData1[peakIndex[peak]]);
						if(peakValue > lastPeak)
							IsGoodTrend = false;
						else
							lastPeak = peakValue;

						if(peak==0)
							listofpeak = listofpeak+" "+ Apeak+"m";
						else
							listofpeak = listofpeak+", "+ Apeak+"m";
					}
					if(IsGoodTrend){
						ArrayList<Entry> values = new ArrayList<>();

						for (int i = 0; i < imageData.length; i++) {

							float val = (float) Math.round(Math.log10(imageData[i])*10)/10;
							float distanceIndex = (float)round(0.33*inputBlockSize*(i)/2/44,2);
							values.add(new Entry(distanceIndex, val));
						}

						LineDataSet SoundData = new LineDataSet(values, "Sound Data");

						// set the line to be drawn like this "- - - - - -"
						SoundData.enableDashedLine(10f, 5f, 0f);
						SoundData.enableDashedHighlightLine(10f, 5f, 0f);
						SoundData.setColor(Color.BLACK);
						SoundData.setCircleColor(Color.BLACK);
						SoundData.setLineWidth(1f);
						SoundData.setCircleRadius(3f);
						SoundData.setDrawCircleHole(false);
						SoundData.setDrawValues(false);
						SoundData.setDrawFilled(true);
						SoundData.setFormLineWidth(1f);
						SoundData.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
						SoundData.setFormSize(15.f);
						SoundData.setDrawCircles(false);

						if (Utils.getSDKInt() >= 18) {
							// fill drawable only supported on api level 18 and above
							Drawable drawable = ContextCompat.getDrawable(this, R.drawable.fade_red);
							SoundData.setFillDrawable(drawable);
						}
						else {
							SoundData.setFillColor(Color.BLACK);
						}

						ArrayList<ILineDataSet> dataSets = new ArrayList<>();
						dataSets.add(SoundData); // add the datasets

						final LineData data = new LineData(dataSets);

						final String inputString = listofpeak;
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								// This gets executed on the UI thread so it can safely modify Views
								mresult.setText( inputString+"\n");
								mChart.clear();
								mChart.setData(data);
//							mChart.animateX(2000);
							}
						});
					}

				}
//				logWriter.flush();
				Thread.sleep(300);	
				
				Intent i = new Intent("android.intent.action.MAIN");
				this.sendBroadcast(i);


				FirstPeakDetected = false;
				HistIndex1 = 0;
				datacounter =0;
			}
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	public double goertzelFilter(int samples[], double freq, int N) {
	    double s_prev = 0.0;
	    double s_prev2 = 0.0;    
	    double coeff,normalizedfreq,power,s;
	    int i;
	    normalizedfreq = freq / AUDIO_SAMPLE_RATE;
	    coeff = 2*Math.cos(2*Math.PI*normalizedfreq);
	    for (i=0; i<N; i++) {
	        s = samples[i] + coeff * s_prev - s_prev2;
	        s_prev2 = s_prev;
	        s_prev = s;
	    }
	    power =s_prev2*s_prev2+s_prev*s_prev-coeff*s_prev*s_prev2;
	    if(power <1){
	    	power =1;
	    }
//	    power = Math.log10(power);
	    return power;
	}
	
	
	public  double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}
	
	public void prepareCounter(){

		TimeCount+= 20;
	}

	public void resetCounter(){

		TimeCount = 0;
	}
	
	public void setElement(TextView t1, LineChart lc)
	{
		mresult = t1;
		mresult.setText("");
		mChart = lc;
		mChart.getDescription().setEnabled(false);
		mChart.getLegend().setEnabled(false);
	}
	
	//停止程序
	public void stop(){
		measureStop();
	}
	@Override
    public void onDestroy() {
    	super.onDestroy();
	}
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		IBinder result = null;
	    if (null == result) {
	        result = new MyBinder();
	    }
	    return result;
	}
	
	public class MyBinder extends Binder{
	    
	    public AudioProcess getService(){
	        return AudioProcess.this;
	    }
	}

}
