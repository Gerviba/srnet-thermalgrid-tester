package hu.gerviba.termalvisualizer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class App extends Application {

	static SerialPort serialPort;
	static GraphicsContext gc;
	static GraphicsContext gcText;
	static boolean tempVisible = false;
	static boolean blurFilter = false;

	static class SerialPortReader implements SerialPortEventListener {

		String termal = "";
		
		public void serialEvent(SerialPortEvent event) {
			if (event.isRXCHAR()) {
				if (event.getEventValue() >= 1) {
					try {
						byte buffer[] = serialPort.readBytes(event.getEventValue());
						termal += new String(buffer);
					} catch (SerialPortException ex) {
						System.out.println(ex);
					}
				}
			} else if (event.isCTS()) {
				if (event.getEventValue() == 1) {
					System.out.println("CTS - ON");
				} else {
					System.out.println("CTS - OFF");
				}
			} else if (event.isDSR()) {
				if (event.getEventValue() == 1) {
					System.out.println("DSR - ON");
				} else {
					System.out.println("DSR - OFF");
				}
			}
			
			if (termal.indexOf(']') != -1) {
				String draw = termal.replaceAll("[\\n\\r]", "")
						.replace("[", "")
						.replace("]", "")
						.replaceAll("[ ]", "");
				termal = "";
				String[] data = draw
						.split("\\,");
				
				try {
					List<Double> dataDoubles = Arrays.asList(data).stream()
							.filter(Objects::nonNull)
							.filter(num -> !num.isEmpty())
							.mapToDouble(Double::parseDouble)
							.boxed()
							.collect(Collectors.toList());
					drawShapes(dataDoubles);
				} catch (Exception e) {
				}
			}
		}
	}

	static int interpolate(int color1, int color2, int color3, float fraction) {
		fraction = Math.min(1.0f, Math.max(0.0f, fraction));
		if (fraction > 0.5f)
			return (int) ((color3 - color2) * ((fraction - 0.5f) * 2.0f) + color2);
		else
			return (int) ((color2 - color1) * (fraction * 2.0f) + color1);
	}
	
	static float range(double value) {
		final float max = 33.0f;
		final float min = 18.0f;
		return Math.min(1.0f, Math.max(0.0f, ((float)value - min)/(max - min)));
	}
	
	public static void main(String[] args) {
		launch(args);
	}

	private static void connectToDevice() {
		for (int i = 0; i < 10; ++i) {
			serialPort = new SerialPort("/dev/ttyUSB" + i);
			try {
				serialPort.openPort();
				serialPort.setParams(115200, 8, 1, 0);
				System.out.println("USB TTY: " + i);
				int mask = SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS + SerialPort.MASK_DSR;
				serialPort.setEventsMask(mask);
				serialPort.addEventListener(new SerialPortReader());
			} catch (SerialPortException ex) {
				System.out.println(ex);
				continue;
			}
			return;
		}
	}
	
	@Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Termal grid 8x8");
        StackPane root = new StackPane();
        Canvas canvas = new Canvas(512, 512);
        gc = canvas.getGraphicsContext2D();
        Canvas canvasText = new Canvas(512, 512);
        gcText = canvas.getGraphicsContext2D();
        root.getChildren().add(canvas);
        root.getChildren().add(canvasText);
        Scene scene = new Scene(root);
		primaryStage.setScene(scene);
        primaryStage.show();
        
        scene.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.T) {
				tempVisible = !tempVisible;
			} else if (event.getCode() == KeyCode.B) {
				blurFilter = !blurFilter;
			}
		});
        
        connectToDevice();
    }
	
	@Override
	public void stop() throws Exception {
		System.out.println("Stopping");
		if (serialPort != null && serialPort.isOpened()) {
			serialPort.closePort();
		}
	}

    private static void drawShapes(List<Double> data) {
    	Platform.runLater(() -> {
	        gc.setFill(Color.BLACK);
	        gc.fillRect(0, 0, 512, 512);
	        
	        if (tempVisible) {
	        	gcText.setFill(Color.TRANSPARENT);
	        	gcText.fillRect(0, 0, 512, 512);
	        }
	        
	        for (int i = 0; i < 64; ++i) {
	        	String heat = "n/a";
	        	if (data.size() <= i) {
	        		gc.setFill(Color.PURPLE);
	        	} else {
		            gc.setFill(new Color(
		            		interpolate(1, 249, 166, range(data.get(i)))/255.0f,
		            		interpolate(105, 247, 1, range(data.get(i)))/255.0f,
		            		interpolate(56, 174, 38, range(data.get(i)))/255.0f,
		            		1.0f));
		            heat = "" + data.get(i);
	        	}
	        	
	            gc.fillRect((7 - (i % 8)) * 64, (7 - (i / 8)) * 64, 64, 64);
	            
	            if (tempVisible) {
		            gcText.setTextAlign(TextAlignment.CENTER);
		            gcText.setFill(Color.BLACK);
		            gcText.fillText(heat + "°C", (7 - (i % 8)) * 64 + 32, (7 - (i / 8)) * 64 + 36);
	            }
	        }
	        
	        if (blurFilter) {
				GaussianBlur blur = new GaussianBlur();
				blur.setRadius(32);
	            
	        	gc.applyEffect(blur);
	        }
    	});
    }
}
