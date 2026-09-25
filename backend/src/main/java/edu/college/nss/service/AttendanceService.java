package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.exception.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.AttendanceDTOs.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final ServiceHourEntryRepository serviceHourRepository;
    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final VolunteerRepository volunteerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisAttendanceSecurityService redisSecurityService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AttendanceService(AttendanceSessionRepository sessionRepository,
                             AttendanceRecordRepository recordRepository,
                             AttendanceCorrectionRepository correctionRepository,
                             ServiceHourEntryRepository serviceHourRepository,
                             EventRepository eventRepository,
                             EventRegistrationRepository registrationRepository,
                             VolunteerRepository volunteerRepository,
                             UserRepository userRepository,
                             NotificationService notificationService,
                             RedisAttendanceSecurityService redisSecurityService) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.correctionRepository = correctionRepository;
        this.serviceHourRepository = serviceHourRepository;
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.volunteerRepository = volunteerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.redisSecurityService = redisSecurityService;
    }

    @Transactional
    public SessionResponse openSession(UUID eventId, CreateSessionRequest req, UserDetails principal) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + eventId));
        assertManagerForUnit(principal, event.getUnit());

        if (!"OPEN".equals(event.getStatus()) && !"COMPLETED".equals(event.getStatus())) {
            throw new IllegalStateException("Attendance sessions can only be opened for OPEN or COMPLETED events.");
        }
        if (!req.expiresAt().isAfter(req.startsAt())) {
            throw new IllegalArgumentException("Expiration time must be strictly after start time.");
        }

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String qrSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        User manager = currentUser(principal);
        AttendanceSession session = new AttendanceSession(event, manager, req.startsAt(), req.expiresAt(), qrSecret);
        AttendanceSession saved = sessionRepository.save(session);

        String signedToken = generateSignedToken(saved);
        long registeredCount = registrationRepository.countRegistered(eventId);

        return toSessionResponse(saved, signedToken, 0, registeredCount);
    }

    @Transactional(readOnly = true)
    public SessionResponse getActiveSession(UUID eventId, UserDetails principal) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + eventId));
        assertManagerForUnit(principal, event.getUnit());

        List<AttendanceSession> active = sessionRepository.findActiveSessionsForEvent(eventId);
        if (active.isEmpty()) {
            return null;
        }
        AttendanceSession session = active.get(0);
        String signedToken = generateSignedToken(session);
        long presentCount = recordRepository.countBySession_SessionIdAndStatus(session.getSessionId(), "PRESENT");
        long totalRegistered = registrationRepository.countRegistered(eventId);

        return toSessionResponse(session, signedToken, presentCount, totalRegistered);
    }

    @Transactional
    public SessionResponse closeSession(UUID sessionId, UserDetails principal) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Attendance session not found with ID: " + sessionId));
        assertManagerForUnit(principal, session.getEvent().getUnit());

        session.close();
        AttendanceSession saved = sessionRepository.save(session);

        // Auto-generate ABSENT records for all confirmed registrants who never checked in
        List<EventRegistration> confirmedRegistrations = registrationRepository
            .findByEvent_EventIdAndStatus(session.getEvent().getEventId(), "CONFIRMED");
        if (confirmedRegistrations.isEmpty()) {
            confirmedRegistrations = registrationRepository
                .findByEvent_EventIdAndStatus(session.getEvent().getEventId(), "REGISTERED");
        }

        List<AttendanceRecord> existingRecords = recordRepository.findBySession_SessionId(sessionId);
        java.util.Set<UUID> checkedInIds = existingRecords.stream()
            .map(r -> r.getVolunteer().getVolunteerId())
            .collect(Collectors.toSet());

        List<AttendanceRecord> absentRecords = new java.util.ArrayList<>();
        for (EventRegistration reg : confirmedRegistrations) {
            if (!checkedInIds.contains(reg.getVolunteer().getVolunteerId())) {
                AttendanceRecord absent = new AttendanceRecord(saved, reg.getVolunteer(), "AUTO_ABSENT", "ABSENT");
                absentRecords.add(absent);
            }
        }
        if (!absentRecords.isEmpty()) {
            recordRepository.saveAll(absentRecords);
        }

        long presentCount = recordRepository.countBySession_SessionIdAndStatus(saved.getSessionId(), "PRESENT");
        long totalRegistered = registrationRepository.countRegistered(saved.getEvent().getEventId());

        return toSessionResponse(saved, null, presentCount, totalRegistered);
    }

    @Transactional
    public CheckInResponse checkInWithQr(CheckInRequest request, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername())
            .orElseThrow(() -> new AccessDeniedException("Only enrolled volunteers can perform QR check-in."));

        if (!"ACTIVE".equalsIgnoreCase(volunteer.getStatus()) || !"ACTIVE".equalsIgnoreCase(volunteer.getUser().getStatus())) {
            throw new AccessDeniedException("Volunteer status must be ACTIVE to record attendance.");
        }

        ParsedToken parsed = parseAndVerifyToken(request.token());
        if (!redisSecurityService.acquireCheckInLock(parsed.sessionId(), volunteer.getVolunteerId())) {
            throw new DuplicateAttendanceException();
        }

        try {
            AttendanceSession session = sessionRepository.findById(parsed.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));

            if (!"OPEN".equals(session.getStatus())) {
                throw new AttendanceSessionExpiredException("Attendance session is closed.");
            }
            Instant now = Instant.now();
            if (now.isAfter(session.getExpiresAt())) {
                session.expire();
                sessionRepository.save(session);
                throw new AttendanceSessionExpiredException("Attendance session has expired.");
            }

            EventRegistration registration = registrationRepository
                .findByEvent_EventIdAndVolunteer_VolunteerId(session.getEvent().getEventId(), volunteer.getVolunteerId())
                .orElseThrow(() -> new AccessDeniedException("You are not registered for this event."));

            if (!"REGISTERED".equals(registration.getStatus()) && !"CONFIRMED".equals(registration.getStatus())) {
                throw new AccessDeniedException("Only volunteers with active REGISTERED or CONFIRMED status may check in.");
            }

            if (recordRepository.existsBySession_SessionIdAndVolunteer_VolunteerId(session.getSessionId(), volunteer.getVolunteerId())) {
                throw new DuplicateAttendanceException();
            }

            AttendanceRecord record = new AttendanceRecord(session, volunteer, "QR", "PRESENT");
            AttendanceRecord savedRecord = recordRepository.save(record);

            creditServiceHours(session.getEvent(), volunteer, savedRecord, null);

            return new CheckInResponse(
                savedRecord.getAttendanceId(),
                volunteer.getVolunteerId(),
                volunteer.getUser().getName(),
                volunteer.getCollegeId(),
                savedRecord.getCheckedInAt(),
                savedRecord.getStatus(),
                savedRecord.getCheckInMethod(),
                "Attendance successfully verified for " + session.getEvent().getTitle()
            );
        } finally {
            redisSecurityService.releaseCheckInLock(parsed.sessionId(), volunteer.getVolunteerId());
        }
    }

    @Transactional
    public CheckInResponse manualCheckIn(UUID sessionId, ManualCheckInRequest req, UserDetails principal) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        assertManagerForUnit(principal, session.getEvent().getUnit());

        Volunteer volunteer = volunteerRepository.findById(req.volunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found: " + req.volunteerId()));

        Optional<AttendanceRecord> existing = recordRepository.findBySession_SessionIdAndVolunteer_VolunteerId(sessionId, volunteer.getVolunteerId());
        AttendanceRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.setStatus(req.status());
            record.setCheckInMethod("MANUAL_COORDINATOR");
        } else {
            record = new AttendanceRecord(session, volunteer, "MANUAL_COORDINATOR", req.status());
        }
        AttendanceRecord saved = recordRepository.save(record);

        if ("PRESENT".equals(req.status())) {
            creditServiceHours(session.getEvent(), volunteer, saved, currentUser(principal));
        }

        return new CheckInResponse(
            saved.getAttendanceId(),
            volunteer.getVolunteerId(),
            volunteer.getUser().getName(),
            volunteer.getCollegeId(),
            saved.getCheckedInAt(),
            saved.getStatus(),
            saved.getCheckInMethod(),
            "Attendance manually updated by coordinator."
        );
    }

    @Transactional
    public CorrectionResponse correctAttendance(UUID attendanceId, CorrectionRequest req, UserDetails principal) {
        if (req.reason() == null || req.reason().trim().isBlank()) {
            throw new IllegalArgumentException("A valid non-blank audit reason is mandatory for attendance corrections.");
        }
        AttendanceRecord record = recordRepository.findById(attendanceId)
            .orElseThrow(() -> new IllegalArgumentException("Attendance record not found: " + attendanceId));

        assertManagerForUnit(principal, record.getSession().getEvent().getUnit());

        String prevStatus = record.getStatus();
        String newStatus = req.newStatus().trim().toUpperCase();
        if (!List.of("PRESENT", "ABSENT", "EXCUSED").contains(newStatus)) {
            throw new IllegalArgumentException("Invalid status. Allowed values: PRESENT, ABSENT, EXCUSED.");
        }

        record.setStatus(newStatus);
        recordRepository.save(record);

        User officer = currentUser(principal);
        AttendanceCorrection correction = new AttendanceCorrection(
            record, officer, prevStatus, newStatus, req.reason().trim()
        );
        AttendanceCorrection savedCorrection = correctionRepository.save(correction);

        syncServiceHoursOnCorrection(record, newStatus, officer);

        return toCorrectionResponse(savedCorrection);
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> getPendingCorrections(UserDetails principal) {
        return correctionRepository.findByStatusOrderByCorrectedAtDesc("PENDING")
            .stream()
            .map(this::toCorrectionResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public CorrectionResponse reviewCorrection(UUID correctionId, edu.college.nss.web.dto.CorrectionReviewRequest req, UserDetails principal) {
        AttendanceCorrection correction = correctionRepository.findById(correctionId)
            .orElseThrow(() -> new IllegalArgumentException("Correction not found: " + correctionId));

        assertManagerForUnit(principal, correction.getAttendanceRecord().getSession().getEvent().getUnit());

        User officer = currentUser(principal);
        correction.setReviewedBy(officer);
        correction.setReviewedAt(Instant.now());
        correction.setReviewRemarks(req.remarks());

        if (Boolean.TRUE.equals(req.approved())) {
            correction.setStatus("APPROVED");
            AttendanceRecord record = correction.getAttendanceRecord();
            record.setStatus(correction.getNewStatus());
            recordRepository.save(record);
            syncServiceHoursOnCorrection(record, correction.getNewStatus(), officer);
        } else {
            correction.setStatus("REJECTED");
        }

        correction = correctionRepository.save(correction);

        if (correction.getAttendanceRecord().getVolunteer().getUser() != null) {
            notificationService.sendNotification(
                correction.getAttendanceRecord().getVolunteer().getUser(),
                "Attendance Correction " + correction.getStatus(),
                "Your attendance correction request has been " + correction.getStatus() + (req.remarks() != null ? ": " + req.remarks() : ""),
                "ATTENDANCE",
                "/events/" + correction.getAttendanceRecord().getSession().getEvent().getEventId()
            );
        }

        return toCorrectionResponse(correction);
    }

    private CorrectionResponse toCorrectionResponse(AttendanceCorrection c) {
        return new CorrectionResponse(
            c.getCorrectionId(),
            c.getAttendanceRecord().getAttendanceId(),
            c.getCorrectedBy().getName(),
            c.getPreviousStatus(),
            c.getNewStatus(),
            c.getReason(),
            c.getStatus(),
            c.getReviewRemarks(),
            c.getReviewedBy() != null ? c.getReviewedBy().getName() : null,
            c.getReviewedAt(),
            c.getCorrectedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<AttendanceRosterItem> getRoster(UUID eventId, UUID sessionId, UserDetails principal) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        assertManagerForUnit(principal, event.getUnit());

        List<EventRegistration> registrations = registrationRepository.findByEvent_EventIdOrderByRegisteredAtAsc(eventId);
        Map<UUID, AttendanceRecord> recordsByVolunteer = new HashMap<>();

        if (sessionId != null) {
            recordRepository.findBySession_SessionId(sessionId)
                .forEach(r -> recordsByVolunteer.put(r.getVolunteer().getVolunteerId(), r));
        } else {
            recordRepository.findByEventId(eventId)
                .forEach(r -> recordsByVolunteer.put(r.getVolunteer().getVolunteerId(), r));
        }

        return registrations.stream().map(reg -> {
            Volunteer v = reg.getVolunteer();
            AttendanceRecord rec = recordsByVolunteer.get(v.getVolunteerId());
            return new AttendanceRosterItem(
                v.getVolunteerId(),
                v.getCollegeId(),
                v.getUser().getName(),
                v.getDepartment(),
                event.getUnit().getUnitNumber(),
                reg.getStatus(),
                rec != null ? rec.getStatus() : "PENDING",
                rec != null ? rec.getCheckInMethod() : null,
                rec != null ? rec.getCheckedInAt() : null,
                rec != null ? rec.getAttendanceId() : null
            );
        }).toList();
    }

    private String generateSignedToken(AttendanceSession session) {
        long issuedAt = Instant.now().toEpochMilli();
        long expiresAt = Math.min(session.getExpiresAt().toEpochMilli(), issuedAt + 60_000L);
        String payload = session.getSessionId() + ":" + session.getEvent().getEventId() + ":" + issuedAt + ":" + expiresAt;
        String signature = hmacSha256(payload, session.getQrSecret());
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    private ParsedToken parseAndVerifyToken(String tokenStr) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(tokenStr);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 5) {
                throw new IllegalArgumentException("Malformed token format.");
            }
            UUID sessionId = UUID.fromString(parts[0]);
            UUID eventId = UUID.fromString(parts[1]);
            long issuedAt = Long.parseLong(parts[2]);
            long expiresAt = Long.parseLong(parts[3]);
            String signature = parts[4];

            if (Instant.now().toEpochMilli() > expiresAt) {
                throw new AttendanceSessionExpiredException("QR check-in token has expired. Please rescan.");
            }

            AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));

            String payload = sessionId + ":" + eventId + ":" + issuedAt + ":" + expiresAt;
            String expectedSignature = hmacSha256(payload, session.getQrSecret());

            if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new AccessDeniedException("Cryptographic QR token validation failed.");
            }

            return new ParsedToken(sessionId, eventId, expiresAt);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid QR check-in token: " + e.getMessage());
        }
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }

    private void creditServiceHours(Event event, Volunteer volunteer, AttendanceRecord record, User approvedBy) {
        long minutes = Math.max(30, Duration.between(event.getStartAt(), event.getEndAt()).toMinutes());
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        Optional<ServiceHourEntry> existing = serviceHourRepository.findByAttendanceRecord_AttendanceId(record.getAttendanceId());
        if (existing.isEmpty()) {
            ServiceHourEntry entry = new ServiceHourEntry(
                volunteer, event, record, hours, "APPROVED", approvedBy, "Verified attendance at " + event.getTitle()
            );
            serviceHourRepository.save(entry);
        }
    }

    private void syncServiceHoursOnCorrection(AttendanceRecord record, String newStatus, User officer) {
        serviceHourRepository.findByAttendanceRecord_AttendanceId(record.getAttendanceId()).ifPresent(entry -> {
            if ("PRESENT".equals(newStatus)) {
                entry.setStatus("APPROVED");
            } else {
                entry.setStatus("REJECTED");
            }
            serviceHourRepository.save(entry);
        });
    }

    private void assertManagerForUnit(UserDetails principal, NssUnit unit) {
        if (hasRole(principal, "ADMIN") || hasRole(principal, "FACULTY_COORDINATOR")) return;
        if (hasRole(principal, "PROGRAMME_OFFICER")) {
            User user = currentUser(principal);
            if (unit.getOfficer() != null && unit.getOfficer().getUserId().equals(user.getUserId())) return;
        }
        throw new AccessDeniedException("You do not have permission to manage attendance for this NSS unit.");
    }

    private boolean hasRole(UserDetails principal, String role) {
        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        String prefixedRole = "ROLE_" + cleanRole;
        return principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(cleanRole) || a.getAuthority().equals(prefixedRole));
    }

    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found for principal: " + principal.getUsername()));
    }

    private SessionResponse toSessionResponse(AttendanceSession s, String token, long present, long total) {
        return new SessionResponse(
            s.getSessionId(),
            s.getEvent().getEventId(),
            s.getEvent().getTitle(),
            s.getOpenedBy().getName(),
            s.getStartsAt(),
            s.getExpiresAt(),
            s.getStatus(),
            token,
            present,
            total
        );
    }

    private record ParsedToken(UUID sessionId, UUID eventId, long expiresAt) {}
}
