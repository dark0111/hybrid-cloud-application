package microservices.api.sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.io.FileInputStream;
import javax.naming.InitialContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import microservices.api.sample.model.Airline;
import microservices.api.sample.model.Booking;
import microservices.api.sample.model.Airport;
import microservices.api.sample.model.Weather;

public class DatabaseAccess {
	
	public static void main (String[] args) {
		getAllAirlines();
	}


	//Please change your database address to your cloud host if you are hosting your Server on the cloud.
	private static String DATABASE_CORE_ADDRESS;

	static {
		Properties prop = new Properties();
		try {
			InitialContext ctx;
			ctx = new InitialContext();
			Object jndiconstant = ctx.lookup("dburl");
			DATABASE_CORE_ADDRESS = (String) jndiconstant;
			System.out.println("Setting database URL to: " + DATABASE_CORE_ADDRESS);
		} catch (Exception e) {
			System.err.println("Could not read database URL from JNDI Context " + e.getMessage());
			System.exit(1);

		}
	}
	
	private static String AIRLINES_DATABASE = DATABASE_CORE_ADDRESS + "airlines";
	private static String BOOKINGS_DATABASE = DATABASE_CORE_ADDRESS + "bookings";
	private static final String ALL_QUERY = "/_all_docs";
	private static final ObjectMapper mapper = new ObjectMapper();

	
	public static Collection<Airline> getAllAirlines() {
		JsonNode response = HttpHelper.connect(AIRLINES_DATABASE + ALL_QUERY, "GET", null);
		if (response == null) {
			return null;
		}
		int size = response.get("total_rows").asInt();
		System.out.println("Number of airlines: " + size);
		JsonNode airlines = response.get("rows");
		List<Airline> allAirlines = new ArrayList<Airline>(size);
		for (int i = 0; i < size; i++) {
			try {
				JsonNode airlineJson = HttpHelper.connect(AIRLINES_DATABASE + "/" + airlines.get(i).get("id").asText(), "GET", null);
				Airline airline = mapper.treeToValue(airlineJson, Airline.class);
				System.out.println("Airline[" + i + "] " + mapper.writeValueAsString(airline));
				allAirlines.add(airline);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return allAirlines;
	}

	public static Weather getAirportWeather(String date, String airportTo, String username, String password) {
		//enable basic login
		HttpHelper.setAuth(username,password);
		HttpHelper.enableAuth(true);
		JsonNode response = HttpHelper.connect("https://twcservice.mybluemix.net/api/weather/v3/location/point?iataCode="+ airportTo +"&language=en-US", "GET", null);
		if (response == null) {
			return null;
		}
		//query the data and get its lat and lon
		String city = response.path("location").path("city").asText();
		double lat = response.path("location").path("latitude").asDouble();
		String latitude = Double.toString(lat);
		double lon = response.path("location").path("longitude").asDouble();
		String longitude = Double.toString(lon);
		JsonNode response2 = HttpHelper.connect("https://twcservice.mybluemix.net/api/weather/v1/geocode/" + latitude + "/" + longitude + "/forecast/daily/10day.json", "GET", null);
		HttpHelper.enableAuth(false);
		JsonNode d10 = response2.get("forecasts");
		try{
			//query data and reformat it to match the Weather class
			DateFormat dates = new SimpleDateFormat("yyyy-MM-dd");
			Date future = dates.parse(date);
			for (JsonNode day : d10) {
				String[] days = day.path("fcst_valid_local").asText().split("T");
				Date dayDate = dates.parse(days[0]);
        		if(future.equals(dayDate)){
        			String weath = day.path("day").path("phrase_22char").asText();
        			int temperture = day.path("day").path("temp").asInt();
        			String narrative = day.path("day").path("narrative").asText();
        			Weather weather = new Weather(date, city, weath, temperture, narrative);
					return weather;
        		}
    		}		
		} catch (Exception e) {
			e.printStackTrace();
		}
		//If we can't find the matching day, return null
		return null;
	}
	
	public static Collection<Booking> getAllBookings() {
		JsonNode response = HttpHelper.connect(BOOKINGS_DATABASE + ALL_QUERY, "GET", null);
		if (response == null) {
			return null;
		}
		int size = response.get("total_rows").asInt();
		System.out.println("Number of bookings: " + size);
		JsonNode bookings = response.get("rows");
		List<Booking> allBookings = new ArrayList<Booking>(size);
		for (int i = 0; i < size; i++) {
			try {
				JsonNode bookingJson = HttpHelper.connect(BOOKINGS_DATABASE + "/" + bookings.get(i).get("id").asText(), "GET", null);
				Booking booking = mapper.treeToValue(bookingJson,Booking.class);
				System.out.println("Booking[" + i + "] " + booking);
				//todo: add id
				allBookings.add(booking);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return allBookings;
	}
	
	public static String addBooking(Booking booking) {
		try {
			JsonNode response = HttpHelper.connect(BOOKINGS_DATABASE, "POST", mapper.writeValueAsString(booking));
			return response.get("id").asText();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Booking getBooking(String id) {
		JsonNode response = HttpHelper.connect(BOOKINGS_DATABASE + "/" + id, "GET", null);
		try {
			return mapper.treeToValue(response,Booking.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void updateBooking(String id, Booking booking) {
		//We need to get the current _rev from the DB first
		JsonNode response = HttpHelper.connect(BOOKINGS_DATABASE + "/" + id, "GET", null);
		String _rev = response.get("_rev").asText();
		
		//Now let's build the new booking
		JsonNode updatedBooking = mapper.valueToTree(booking);
		((ObjectNode)updatedBooking).put("_id", id);
		((ObjectNode)updatedBooking).put("_rev", _rev);

		//Update database
		try {
			HttpHelper.connect(BOOKINGS_DATABASE + "/" + id, "PUT", mapper.writeValueAsString(updatedBooking));
			//TODO: handle update conflict case
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}

	public static void removeBooking(String id) {
		//We need to get the current _rev from the DB first
		JsonNode response = HttpHelper.connect(BOOKINGS_DATABASE + "/" + id, "GET", null);
		String rev = response.get("_rev").asText();
		
		//Issue with delete command, with rev as a query param
		HttpHelper.connect(BOOKINGS_DATABASE + "/" + id + "?rev=" + rev, "DELETE", null);
		//TODO: handle update conflict case
	}
	
}
