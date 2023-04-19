package fpscanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
//SOURCEAFIS
import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;
import java.io.File;
//PICAM -- Legacy camera code; ENABLE RaspiStill: "Bullseye users can also still restore access to raspistill and raspivid. First, make sure the OS is up to date, then open a Terminal window and type sudo raspi-config. Go to Interface Options, then select Legacy Camera and reboot."
import uk.co.caprica.picam.Camera;
import uk.co.caprica.picam.CameraConfiguration;
import uk.co.caprica.picam.CameraException;
import uk.co.caprica.picam.CaptureFailedException;
import uk.co.caprica.picam.FilePictureCaptureHandler;
import uk.co.caprica.picam.NativeLibraryException;
import uk.co.caprica.picam.PictureCaptureHandler;
import uk.co.caprica.picam.enums.AutomaticWhiteBalanceMode;
import uk.co.caprica.picam.enums.Encoding;
import uk.co.caprica.picam.enums.ImageEffect;
import static uk.co.caprica.picam.CameraConfiguration.cameraConfiguration;
import static uk.co.caprica.picam.PicamNativeLibrary.installTempLibrary;
//jframe
import java.awt.Image;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
public class FPScanner {
	private static JFrame frame = null;
	static Scanner input = new Scanner(System.in);
	public static void main(String[] args) throws IOException, NativeLibraryException {
		int mode = 4;
		final int threshold = 35;
		String filePath = "/home/fp5/Desktop/fp5/fp5-main/prints/printDB/"; // enter the filepath of prints folder
		//camera Configuration
		installTempLibrary();
		CameraConfiguration config = cameraConfiguration()
		.width(500)
		.height(500)
		//.crop(50, 50, 50, 50)
		.quality(100)
		.contrast(100)
		.brightness(40)
		.imageEffect(ImageEffect.NEGATIVE)
		.colourEffect(true, 128, 128)
		.encoding(Encoding.PNG);

		do {
			/**MENU**/
			System.out.println("Enter 1 for Enrollment, 2 for Verification, 3 for Identification, or 0 to quit.");
			try {
			mode = input.nextInt();
			}catch(Exception e) {
				System.out.println(e.getMessage());
			}
			switch (mode) {
			case 0: System.out.print("Program Terminated.\n");
					break;
			case 1: System.out.println("Enrollment");
					String first;
					String last;
					
					Scanner name = new Scanner(System.in);
					System.out.println("Enter your first name: ");
					first = name.nextLine();
					System.out.println("Enter your last name: ");
					last = name.nextLine();
					String imageName = first + "_" + last + ".png"; //creates a name for the image
					File file = new File(filePath+imageName);
					if(file.exists()){
						System.out.println("This image already exists and may be enrolled; continuing will overwrite the fp image and may result in a duplicate profile. If you believe this is an error, contact a system administrator.");	
					}
					//Camera implementation 
					System.out.println("Present Biometric and press space to take an image. Press q to return to menu.");
					String capture = "";
					while(!capture.equals("q")){
						capture = input.nextLine();
						if (capture.equals(" ")){
							printScanMessage();
							try(Camera cam = new Camera(config)){
								if(file.exists()){
									file.delete();
								}
								cam.takePicture(new FilePictureCaptureHandler(file),1000);
							}
							catch(CameraException | CaptureFailedException e){
								e.printStackTrace();
							}
							imgWindow(file);
							System.out.println("Press space to take another image, or 'q' to confirm image.");
						}
					}
					if(file.exists()){
						FingerprintTemplate candidate = new FingerprintTemplate(new FingerprintImage(Files.readAllBytes(Paths.get(filePath + imageName))));
						byte[] data = candidate.toByteArray();
						//send template to DB using enrollQuery
						enrollQuery(first,last,data);
					}
					
					break;
			case 2: System.out.println("Verification");
					String first_name;
					String last_name;
					byte[] verification_template = null;
					//call for user input
					Scanner verification_name = new Scanner(System.in);
					System.out.println("Enter your first name: ");
					first_name = verification_name.nextLine();
					System.out.println("Enter your last name: ");
					last_name = verification_name.nextLine();
					String verification_imageName =  first_name + "_" + last_name + "_ver.png";;
					File verification_file = new File(filePath+verification_imageName);
					//Camera implementation 
					System.out.println("Present Biometric and press space to take an image. Press q to return to menu.");
					capture = "";
					while(!capture.equals("q")){
						capture = input.nextLine();
						if (capture.equals(" ")){
							printScanMessage();
							try(Camera cam = new Camera(config)){
								if(verification_file.exists()){
									verification_file.delete();
								}
								cam.takePicture(new FilePictureCaptureHandler(verification_file),1000);
							}
							catch(CameraException | CaptureFailedException e){
								e.printStackTrace();
							}
							imgWindow(verification_file);
							System.out.println("Press space to take another image, or 'q' to confirm image.");
						}
					}
					//biometric data
					FingerprintTemplate candidate_verification = new FingerprintTemplate(
							   new FingerprintImage(Files.readAllBytes(Paths.get(filePath+verification_imageName))));
					//gather template data from DB
					ResultSet verification_data = retrieveQuery(first_name,last_name);
					try {
						if(verification_data.next()) {
							verification_template = verification_data.getBytes("template");
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
						FingerprintTemplate ver_candidate = new FingerprintTemplate(verification_template);
						//compare template
					FingerprintMatcher matcher = new FingerprintMatcher(candidate_verification);
					double similarity = matcher.match(ver_candidate);
					System.out.println("The similarity score is : " + similarity + ".");
					if(similarity>=threshold){System.out.println("You are a Match!");}	
					if(similarity<threshold){System.out.println("You are not a Match.");}	
					//output similarity score and match status.
					//delete verification fingerprint image
					verification_file.delete();
					break;
			case 3: File identification_file = new File(filePath+"identify.png");
					List<Double> scores = new ArrayList<Double>();
					List<Integer> user_ids = new ArrayList<Integer>();
					List<String> identify_first = new ArrayList<String>();
					List<String> identify_last = new ArrayList<String>();
					System.out.println("Identification");
					//Camera implementation 
					System.out.println("Present Biometric and press space to take an image. Press q to return to menu.");
					capture = "";
					while(!capture.equals("q")){
						capture = input.nextLine();
						if (capture.equals(" ")){
							printScanMessage();
							try(Camera cam = new Camera(config)){
								if(identification_file.exists()){
									identification_file.delete();
								}
								cam.takePicture(new FilePictureCaptureHandler(identification_file),1000);
							}
							catch(CameraException | CaptureFailedException e){
								e.printStackTrace();
							}
							imgWindow(identification_file);
							System.out.println("Press space to take another image, or 'q' to confirm image.");
						}
					}
					//placeholder biometric
					FingerprintTemplate identity_probe = new FingerprintTemplate(
							   new FingerprintImage(Files.readAllBytes(Paths.get(filePath+"identify.png"))));
					ResultSet candidates = identifyQuery();
					//delete img after creating a template
					identification_file.delete();
					try {
						int i = 0;
						while(candidates.next()) {
							user_ids.add(i, candidates.getInt("user_id"));
							identify_first.add(i, candidates.getString("fname"));
							identify_last.add(i, candidates.getString("lname"));
							FingerprintTemplate identity_candidate= new FingerprintTemplate(candidates.getBytes("template"));
							FingerprintMatcher identity_matcher = new FingerprintMatcher(identity_probe);
							scores.add(i, identity_matcher.match(identity_candidate));
							System.out.println("Similarity score: " + scores.get(i) + " || " + identify_first.get(i) + " " + identify_last.get(i) );

							i++;
						}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						//index identifier
						double max = Double.NEGATIVE_INFINITY;
						int index = Integer.MIN_VALUE;
						int j = 0;
				        for (double temp : scores) {
				            if (scores.get(j) > max) {
				                max = scores.get(j);
				                index = j;
				            }
				            j++;
				        }
						if(max < threshold){
							System.out.println("The closest user is : " + identify_first.get(index) + " " + identify_last.get(index) + ", but no definitive matches were found.");
						}
						else{
							System.out.println("The user is : " + identify_first.get(index) + " " + identify_last.get(index));
						}
					break;
			default: break;
			}
		}while(mode != 0);
		System.exit(0);
	}
	/**HELPER METHODS**/
	public static void imgWindow(File file) throws IOException{
			//close the window if already open
			if(frame != null){
				frame.dispose();
			}
			//create an image var
			Image img = ImageIO.read(file);
			//create a jframe window and display the image
				//frame is a static var in order to close window when new image is created
			frame = new JFrame(null, null);
			JLabel label = new JLabel(new ImageIcon(img));
			frame.add(label);
			frame.pack();
			frame.setVisible(true);
	}
	public static void enrollQuery(String fname, String lname, byte[] data) {
		try {
			//int result
			Connection conn = DriverManager.getConnection("jdbc:mysql://68.185.142.224:3306/fp5","remote","welcome");
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (fname, lname, template) VALUES (?, ?, ?)");
		      stmt.setString(1, fname);
		      stmt.setString(2, lname);
		      stmt.setBytes(3, data);
		      stmt.executeUpdate();
		      System.out.println("User enrolled successfully.");
		      conn.close();
		    } catch (SQLException e) {
		      System.out.println("Error inserting user: " + e.getMessage());
		    }
		  }
	public static ResultSet retrieveQuery(String fname, String lname) {
		ResultSet result = null;
		try {
			Connection conn = DriverManager.getConnection("jdbc:mysql://68.185.142.224:3306/fp5","remote","welcome");
			PreparedStatement stmt = conn.prepareStatement("SELECT template FROM users WHERE fname = ? and lname = ?");
		    stmt.setString(1, fname);
		    stmt.setString(2, lname);
		    result = stmt.executeQuery();
		    System.out.println("Verification Data Successfully Retrieved.");
	    }catch (SQLException e) {
	    	System.out.println("Error retrieving user: " + e.getMessage());
	    }
		return result;
	  }

	public static ResultSet identifyQuery() {
		ResultSet result = null;
		try {
			Connection conn = DriverManager.getConnection("jdbc:mysql://68.185.142.224:3306/fp5","remote","welcome");
			PreparedStatement stmt = conn.prepareStatement("SELECT user_id, fname, lname, template FROM users");
		    result = stmt.executeQuery();
		    System.out.println("Identification Data Successfully Retrieved.");
	    }catch (SQLException e) {
	    	System.out.println("Error retrieving user(s): " + e.getMessage());
	    }
		return result;
	}

	public static void printScanMessage() {
		System.out.print("Scanning print");
		try {
			Thread.sleep(250);
			System.out.print(".");
			Thread.sleep(250);
			System.out.print(".");
			Thread.sleep(250);
			System.out.println(".");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}


