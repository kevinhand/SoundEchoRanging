package nus.hande.playsound;

public class Window {
	public double[] data =new double[] {1,1,1};

	public Window(double[] Indata){
		this.setData(Indata);
	}
	
	public void Forward(double d){
		data[0]= data[1];
		data[1]=data[2];
		data[2] = d;
	}

	public double[] getData() {
		return data;
	}

	public void setData(double[] data) {
		this.data = data;
	}
	
	public double getLast(){
		return data[2];
	}

}
