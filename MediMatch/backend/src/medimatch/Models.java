package medimatch;

import org.springframework.beans.factory.annotation.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.*;

import java.time.*;
import java.util.*;

// MongoDB Document - Classification

abstract class User {
    @Id public String id;
    public String email, password, name, phone, city, address;
    public double latitude, longitude;
}

@Document("donors")
class Donor extends User {
    public String bloodGroup, gender, medicalConditions;
    public int age, weight;
    public boolean isAvailable = true;
    public LocalDate lastDonationDate;
    public int totalDonations = 0;

    public boolean isEligibleToDonate() {
        if (weight < 50 || age < 18 || age > 65) return false;
        if (lastDonationDate != null && lastDonationDate.plusDays(90).isAfter(LocalDate.now())) return false;
        return isAvailable;
    }
}

@Document("receivers")
class Receiver extends User {
    public String bloodGroupRequired, patientName, hospitalName;
    public boolean hasActiveRequest = false;
}

abstract class Organization {
    @Id public String id;
    public String email, password, name, phone, city, address, registrationNumber;
    public double latitude, longitude;
}

@Document("hospitals")
class Hospital extends Organization {
    public String hospitalType, contactPersonName;
    public boolean bloodBankAvailable, emergencyServices;
    public int bedCount;
    public Map<String, Integer> bloodInventory = new HashMap<>();
}

@Document("bloodrequests")
class BloodRequest {
    @Id public String id;
    public String receiverId, receiverName, bloodGroupRequired, patientName, hospitalName, city, notes;
    public String urgencyLevel = "NORMAL";
    public String status = "OPEN";
    public double latitude, longitude;
    public int unitsRequired = 1;
    public long createdAt = System.currentTimeMillis();

    // Get urgency priority (lower number = more urgent)
    public static int getUrgencyScore(String level) {
        if ("EMERGENCY".equals(level)) return 1;
        if ("URGENT".equals(level)) return 5;
        return 10;
    }

    // Sort list by urgency first, then by time
    public static void sortByUrgency(List<BloodRequest> list) {
        Collections.sort(list, (a, b) -> {
            int diff = getUrgencyScore(a.urgencyLevel) - getUrgencyScore(b.urgencyLevel);
            if (diff != 0) return diff;
            return Long.compare(a.createdAt, b.createdAt);
        });
    }
}

// Repository Classes - Handle all database operations

@Component
class DonorRepository {
    @Autowired MongoTemplate mongo;

    public Donor save(Donor d) {
        return mongo.save(d);
    }

    public Donor findById(String id) {
        return mongo.findById(id, Donor.class);
    }

    public Donor findByEmail(String email) {
        Query q = new Query(Criteria.where("email").is(email));
        return mongo.findOne(q, Donor.class);
    }

    public boolean existsByEmail(String email) {
        Query q = new Query(Criteria.where("email").is(email));
        return mongo.exists(q, Donor.class);
    }

    public List<Donor> findByBloodGroupIn(List<String> groups) {
        Query q = new Query(Criteria.where("bloodGroup").in(groups));
        return mongo.find(q, Donor.class);
    }

    public long count() {
        return mongo.count(new Query(), Donor.class);
    }
}

@Component
class ReceiverRepository {
    @Autowired MongoTemplate mongo;

    public Receiver save(Receiver r) {
        return mongo.save(r);
    }

    public Receiver findById(String id) {
        return mongo.findById(id, Receiver.class);
    }

    public Receiver findByEmail(String email) {
        Query q = new Query(Criteria.where("email").is(email));
        return mongo.findOne(q, Receiver.class);
    }

    public boolean existsByEmail(String email) {
        Query q = new Query(Criteria.where("email").is(email));
        return mongo.exists(q, Receiver.class);
    }
}

@Component
class HospitalRepository {
    @Autowired MongoTemplate mongo;

    public Hospital save(Hospital h) {
        return mongo.save(h);
    }

    public Hospital findById(String id) {
        return mongo.findById(id, Hospital.class);
    }

    public Hospital findByEmail(String email) {
        Query q = new Query(Criteria.where("email").is(email));
        return mongo.findOne(q, Hospital.class);
    }

    public boolean existsByEmail(String email) {
        Query q = new Query(Criteria.where("email").is(email));
        return mongo.exists(q, Hospital.class);
    }

    public List<Hospital> findAll() {
        return mongo.findAll(Hospital.class);
    }

    public long count() {
        return mongo.count(new Query(), Hospital.class);
    }
}

@Component
class BloodRequestRepository {
    @Autowired MongoTemplate mongo;

    public BloodRequest save(BloodRequest r) {
        return mongo.save(r);
    }

    public BloodRequest findById(String id) {
        return mongo.findById(id, BloodRequest.class);
    }

    public List<BloodRequest> findByReceiverId(String receiverId) {
        Query q = new Query(Criteria.where("receiverId").is(receiverId));
        return mongo.find(q, BloodRequest.class);
    }

    public List<BloodRequest> findByStatus(String status) {
        Query q = new Query(Criteria.where("status").is(status));
        return mongo.find(q, BloodRequest.class);
    }

    public List<BloodRequest> findOpenEmergencies() {
        Query q = new Query(
            Criteria.where("status").is("OPEN").and("urgencyLevel").is("EMERGENCY")
        );
        return mongo.find(q, BloodRequest.class);
    }

    public List<BloodRequest> findOpenRequestsForGroups(List<String> groups) {
        Query q = new Query(
            Criteria.where("status").is("OPEN").and("bloodGroupRequired").in(groups)
        );
        return mongo.find(q, BloodRequest.class);
    }
}
