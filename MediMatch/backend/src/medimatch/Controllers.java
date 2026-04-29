package medimatch;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.core.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

// Handles login and signup for all user types

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
class AuthController {

    @Autowired DonorRepository donors;
    @Autowired ReceiverRepository receivers;
    @Autowired HospitalRepository hospitals;
    @Autowired JwtUtil jwt;
    @Autowired PasswordEncoder encoder;

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        // Try each user type one by one
        Donor donor = donors.findByEmail(email);
        if (donor != null && encoder.matches(password, donor.password)) {
            return makeToken(jwt.generate(email, "DONOR", donor.id), "DONOR", donor.id, donor.name, email);
        }

        Receiver receiver = receivers.findByEmail(email);
        if (receiver != null && encoder.matches(password, receiver.password)) {
            return makeToken(jwt.generate(email, "RECEIVER", receiver.id), "RECEIVER", receiver.id, receiver.name, email);
        }

        Hospital hospital = hospitals.findByEmail(email);
        if (hospital != null && encoder.matches(password, hospital.password)) {
            return makeToken(jwt.generate(email, "HOSPITAL", hospital.id), "HOSPITAL", hospital.id, hospital.name, email);
        }

        return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password."));
    }

    @PostMapping("/signup/donor")
    ResponseEntity<?> signupDonor(@RequestBody Donor d) {
        if (donors.existsByEmail(d.email)) {
            return ResponseEntity.status(409).body(Map.of("error", "Email already registered."));
        }
        d.password = encoder.encode(d.password);
        Donor saved = donors.save(d);
        return ResponseEntity.ok(Map.of("message", "Registered successfully! You can now login.", "id", saved.id));
    }

    @PostMapping("/signup/receiver")
    ResponseEntity<?> signupReceiver(@RequestBody Receiver r) {
        if (receivers.existsByEmail(r.email)) {
            return ResponseEntity.status(409).body(Map.of("error", "Email already registered."));
        }
        r.password = encoder.encode(r.password);
        Receiver saved = receivers.save(r);
        return ResponseEntity.ok(Map.of("message", "Registered successfully! You can now login.", "id", saved.id));
    }

    @PostMapping("/signup/hospital")
    ResponseEntity<?> signupHospital(@RequestBody Hospital h) {
        if (hospitals.existsByEmail(h.email)) {
            return ResponseEntity.status(409).body(Map.of("error", "Email already registered."));
        }
        h.password = encoder.encode(h.password);
        Hospital saved = hospitals.save(h);
        return ResponseEntity.ok(Map.of("message", "Hospital registered!", "id", saved.id));
    }

    // Helper to build token response
    private ResponseEntity<?> makeToken(String token, String role, String id, String name, String email) {
        HashMap<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("role", role);
        response.put("userId", id);
        response.put("name", name);
        response.put("email", email);
        return ResponseEntity.ok(response);
    }
}

// Donor logic

@RestController
@RequestMapping("/api/donor")
@CrossOrigin(origins = "*")
class DonorController {

    @Autowired DonorRepository donors;
    @Autowired BloodRequestRepository requests;
    @Autowired HospitalRepository hospitals;
    @Autowired GeoUtils geo;

    @GetMapping("/profile")
    ResponseEntity<?> profile(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Donor d = donors.findById(getUserId(auth));
        if (d == null) return ResponseEntity.notFound().build();
        d.password = null;
        return ResponseEntity.ok(d);
    }

    @PatchMapping("/availability")
    ResponseEntity<?> toggleAvailability(@RequestBody Map<String, Boolean> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Donor d = donors.findById(getUserId(auth));
        if (d == null) return ResponseEntity.notFound().build();
        d.isAvailable = body.getOrDefault("isAvailable", d.isAvailable);
        donors.save(d);
        return ResponseEntity.ok(Map.of("isAvailable", d.isAvailable));
    }

    @GetMapping("/open-requests")
    ResponseEntity<?> openRequests(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Donor d = donors.findById(getUserId(auth));
        if (d == null) return ResponseEntity.notFound().build();

        List<String> compatible = geo.compatibleDonorGroups(d.bloodGroup);
        List<BloodRequest> list = requests.findOpenRequestsForGroups(compatible);
        BloodRequest.sortByUrgency(list);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/emergencies")
    ResponseEntity<?> emergencies(@RequestParam double lat, @RequestParam double lng, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Donor d = donors.findById(getUserId(auth));
        if (d == null) return ResponseEntity.notFound().build();

        List<String> compatible = geo.compatibleDonorGroups(d.bloodGroup);
        List<BloodRequest> allEmergencies = requests.findOpenEmergencies();

        // Filter: matching blood group + within 50km
        List<BloodRequest> nearby = new ArrayList<>();
        for (BloodRequest r : allEmergencies) {
            if (compatible.contains(r.bloodGroupRequired)
                    && geo.withinRadius(lat, lng, r.latitude, r.longitude, 50)) {
                nearby.add(r);
            }
        }
        return ResponseEntity.ok(nearby);
    }

    @GetMapping("/nearby-locations")
    ResponseEntity<?> nearbyLocations(@RequestParam double lat, @RequestParam double lng,
                                       @RequestParam(defaultValue = "25") double r) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Add nearby hospitals
        for (Hospital h : hospitals.findAll()) {
            if (geo.withinRadius(lat, lng, h.latitude, h.longitude, r)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", h.id);
                m.put("name", h.name);
                m.put("type", "HOSPITAL");
                m.put("lat", h.latitude);
                m.put("lng", h.longitude);
                m.put("address", h.address);
                m.put("phone", h.phone);
                m.put("distanceKm", geo.distance(lat, lng, h.latitude, h.longitude));
                m.put("additionalInfo", h.bloodBankAvailable ? "Blood Bank Available" : "No Blood Bank");
                results.add(m);
            }
        }

        // Sort by distance (closest first)
        results.sort((a, b) -> Double.compare((Double) a.get("distanceKm"), (Double) b.get("distanceKm")));
        return ResponseEntity.ok(results);
    }

    @PostMapping("/fulfill/{requestId}")
    ResponseEntity<?> fulfill(@PathVariable String requestId, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        BloodRequest req = requests.findById(requestId);
        if (req == null) return ResponseEntity.notFound().build();

        req.status = "FULFILLED";
        requests.save(req);

        Donor d = donors.findById(getUserId(auth));
        if (d != null) {
            d.totalDonations++;
            donors.save(d);
        }
        return ResponseEntity.ok(Map.of("message", "Request marked as fulfilled. Thank you!"));
    }

    private String getUserId(Authentication auth) {
        if (auth == null) return null;
        return (String) auth.getCredentials();
    }
}

// Receiver logic

@RestController
@RequestMapping("/api/receiver")
@CrossOrigin(origins = "*")
class ReceiverController {

    @Autowired ReceiverRepository receivers;
    @Autowired BloodRequestRepository requests;
    @Autowired DonorRepository donors;
    @Autowired EmailService emailService;
    @Autowired GeoUtils geo;

    @GetMapping("/profile")
    ResponseEntity<?> profile(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Receiver r = receivers.findById(getUserId(auth));
        if (r == null) return ResponseEntity.notFound().build();
        r.password = null;
        return ResponseEntity.ok(r);
    }

    @GetMapping("/my-requests")
    ResponseEntity<?> myRequests(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(requests.findByReceiverId(getUserId(auth)));
    }

    @PostMapping("/request")
    ResponseEntity<?> createRequest(@RequestBody BloodRequest req, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Receiver r = receivers.findById(getUserId(auth));
        if (r == null) return ResponseEntity.notFound().build();

        req.receiverId = r.id;
        req.receiverName = r.name;
        BloodRequest saved = requests.save(req);
        r.hasActiveRequest = true;
        receivers.save(r);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/emergency")
    ResponseEntity<?> triggerEmergency(@RequestBody BloodRequest req, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Receiver r = receivers.findById(getUserId(auth));
        if (r == null) return ResponseEntity.notFound().build();

        req.receiverId = r.id;
        req.receiverName = r.name;
        req.urgencyLevel = "EMERGENCY";
        BloodRequest saved = requests.save(req);
        emailService.sendEmergencyAlerts(saved);

        HashMap<String, Object> response = new HashMap<>();
        response.put("message", "Emergency alert sent to nearby donors!");
        response.put("requestId", saved.id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nearby-donors")
    ResponseEntity<?> nearbyDonors(@RequestParam double lat, @RequestParam double lng,
                                    @RequestParam String bloodGroup,
                                    @RequestParam(defaultValue = "20") double radius) {
        bloodGroup = bloodGroup.trim();
        List<String> compatible = geo.compatibleDonorGroups(bloodGroup);
        List<Donor> allDonors = donors.findByBloodGroupIn(compatible);

        // Filter eligible donors within radius
        List<Map<String, Object>> donors = new ArrayList<>();
        for (Donor d : allDonors) {
            if (d.isAvailable && d.isEligibleToDonate()
                    && geo.withinRadius(lat, lng, d.latitude, d.longitude, radius)) {
                double dist = geo.distance(lat, lng, d.latitude, d.longitude);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.id);
                m.put("name", d.name);
                m.put("bloodGroup", d.bloodGroup);
                m.put("phone", d.phone);
                m.put("city", d.city);
                m.put("latitude", d.latitude);
                m.put("longitude", d.longitude);
                m.put("distanceKm", dist);
                m.put("distanceText", geo.formatDistance(dist));
                donors.add(m);
            }
        }

        // Sort by distance (closest first)
        donors.sort((a, b) -> Double.compare((Double) a.get("distanceKm"), (Double) b.get("distanceKm")));
        return ResponseEntity.ok(donors);
    }

    private String getUserId(Authentication auth) {
        if (auth == null) return null;
        return (String) auth.getCredentials();
    }
}

// Hospital logic

@RestController
@RequestMapping("/api/hospital")
@CrossOrigin(origins = "*")
class HospitalController {

    @Autowired HospitalRepository hospitals;
    @Autowired BloodRequestRepository requests;

    @GetMapping("/profile")
    ResponseEntity<?> profile(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Hospital h = hospitals.findById(getUserId(auth));
        if (h == null) return ResponseEntity.notFound().build();
        h.password = null;
        return ResponseEntity.ok(h);
    }

    @GetMapping("/requests")
    ResponseEntity<?> requests() {
        List<BloodRequest> open = requests.findByStatus("OPEN");
        BloodRequest.sortByUrgency(open);
        return ResponseEntity.ok(open);
    }

    @PatchMapping("/inventory")
    ResponseEntity<?> updateInventory(@RequestBody Map<String, Integer> inventory, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Hospital h = hospitals.findById(getUserId(auth));
        if (h == null) return ResponseEntity.notFound().build();
        h.bloodInventory.putAll(inventory);
        hospitals.save(h);

        HashMap<String, Object> response = new HashMap<>();
        response.put("message", "Inventory updated.");
        response.put("inventory", h.bloodInventory);
        return ResponseEntity.ok(response);
    }

    private String getUserId(Authentication auth) {
        if (auth == null) return null;
        return (String) auth.getCredentials();
    }
}

// Other public endpoints

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "*")
class PublicController {

    @Autowired BloodRequestRepository requests;
    @Autowired HospitalRepository hospitals;
    @Autowired DonorRepository donors;

    @GetMapping("/stats")
    ResponseEntity<?> stats() {
        HashMap<String, Object> stats = new HashMap<>();
        stats.put("openRequests", requests.findByStatus("OPEN").size());
        stats.put("emergencies", requests.findOpenEmergencies().size());
        stats.put("hospitals", hospitals.count());
        stats.put("donors", donors.count());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/emergencies")
    ResponseEntity<?> emergencies() {
        List<BloodRequest> list = requests.findOpenEmergencies();
        BloodRequest.sortByUrgency(list);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/health")
    ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "app", "MediMatch", "version", "1.0.0"));
    }
}

// Sends emergency alert emails to nearby donors

@Service
class EmailService {

    @Autowired JavaMailSender mailer;
    @Autowired DonorRepository donors;
    @Autowired GeoUtils geo;

    @Value("${medimatch.emergency.radius.km}") double radius;
    @Value("${spring.mail.username}") String from;

    @Async
    public void sendEmergencyAlerts(BloodRequest req) {
        List<String> compatible = geo.compatibleDonorGroups(req.bloodGroupRequired);
        List<Donor> allDonors = donors.findByBloodGroupIn(compatible);

        for (Donor d : allDonors) {
            // Only email eligible donors within radius
            if (!d.isAvailable || !d.isEligibleToDonate()) continue;
            if (!geo.withinRadius(req.latitude, req.longitude, d.latitude, d.longitude, radius)) continue;

            try {
                String body = "Hi " + d.name + ",\n\n"
                    + "EMERGENCY BLOOD REQUEST\n\n"
                    + "Blood Group Needed: " + req.bloodGroupRequired + "\n"
                    + "Patient: " + req.patientName + "\n"
                    + "Hospital: " + req.hospitalName + "\n"
                    + "Units Required: " + req.unitsRequired + "\n"
                    + "Location: " + req.city + "\n";

                if (req.notes != null && !req.notes.isEmpty()) {
                    body += "Notes: " + req.notes + "\n";
                }
                body += "\nPlease login to MediMatch and respond to this request.\nThank you for saving lives!";

                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(d.email);
                msg.setSubject("Emergency Blood Request - " + req.bloodGroupRequired + " Needed");
                msg.setText(body);
                mailer.send(msg);
                System.out.println("Email sent to: " + d.email);
            } catch (Exception e) {
                System.out.println("Error sending email to " + d.email + ": " + e.getMessage());
            }
        }
    }
}
