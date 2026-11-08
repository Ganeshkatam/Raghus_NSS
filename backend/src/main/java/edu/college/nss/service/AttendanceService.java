package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.exception.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.AttendanceDTOs.*;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UnitMembershipRepository membershipRepository;
    private final edu.college.nss.security.UnitSecurityService unitSecurity;
    private final AuditService auditService;
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
                             RedisAttendanceSecurityService redisSecurityService,
                             UnitMembershipRepository membershipRepository,
                             edu.college.nss.security.UnitSecurityService unitSecurity,
                             AuditService auditService) {
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
        this.membershipRepository = membershipRepository;
        this.unitSecurity = unitSecurity;
        this.auditService = auditService;
    }

    @Transactional
    public SessionResponse openSession(UUID eventId, CreateSessionRequest req, UserDetails principal) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + eventId));
        assertManagerForUnit(principal, event.getUnit());

        if (!"OPEN".equals(event.getStatus())) {
            throw new IllegalStateException("Attendance sessions can only be opened for OPEN events.");
        }
        if (!req.expiresAt().isAfter(req.startsAt())) {
            throw new IllegalArgumentException("Expiration time must be strictly after start time.");
        }

        List<AttendanceSession> activeSessions = sessionRepository.findActiveSessionsForEvent(eventId);
        if (!activeSessions.isEmpty()) {
            throw new IllegalStateException("An active attendance session is already open for this event. Please close the active session first.");
        }

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String qrSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        User manager = currentUser(principal);
        AttendanceSession session = new AttendanceSession(event, manager, req.startsAt(), req.expiresAt(), qrSecret);
        AttendanceSession saved = sessionRepository.save(session);

        String signedToken = generateSignedToken(saved);
        long registeredCount = registrationRepository.countRegistered(eventId);

        auditService.logEvent(
            principal.getUsername(),
            "ATTENDANCE_SESSION_OPEN",
            "ATTENDANCE_SESSION",
            saved.getSessionId().toString(),
            event.getTitle(),
            "NONE",
            "OPEN",
            "Opened attendance session from " + req.startsAt() + " to " + req.expiresAt()
        );

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

        auditService.logEvent(
            principal.getUsername(),
            "ATTENDANCE_SESSION_CLOSE",
            "ATTENDANCE_SESSION",
            saved.getSessionId().toString(),
            saved.getEvent().getTitle(),
            "OPEN",
            "CLOSED",
            "Closed attendance session with " + presentCount + " present, " + absentRecords.size() + " marked auto-absent."
        );

        return toSessionResponse(saved, null, presentCount, totalRegistered);
    }

    @Transactional
    public CheckInResponse checkInWithQr(CheckInRequest request, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername())
            .orElseThrow(() -> new AccessDeniedException("Only enrolled volunteers can perform QR check-in."));

        if (!"ACTIVE".equalsIgnoreCase(volunteer.getStatus()) ||
            (volunteer.getUser() != null && !"ACTIVE".equalsIgnoreCase(volunteer.getUser().getStatus()))) {
            throw new AccessDeniedException("Volunteer status must be ACTIVE to record attendance.");
        }

        ParsedToken parsed = parseAndVerifyToken(request.token());

        // Optional Redis concurrency limiter (non-authoritative; DB is authoritative)
        boolean redisLockAcquired = false;
        try {
            redisLockAcquired = redisSecurityService.acquireCheckInLock(parsed.sessionId(), volunteer.getVolunteerId());
        } catch (Exception ex) {
            // Redis failures are non-fatal, proceed with database-authoritative execution
        }

        try {
            AttendanceSession session = sessionRepository.findById(parsed.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));

            validateSessionAndEvent(session, Instant.now());

            EventRegistration registration = registrationRepository
                .findByEvent_EventIdAndVolunteer_VolunteerId(session.getEvent().getEventId(), volunteer.getVolunteerId())
                .orElseThrow(() -> new AccessDeniedException("You are not registered for this event."));

            if (!"REGISTERED".equals(registration.getStatus()) && !"CONFIRMED".equals(registration.getStatus())) {
                throw new AccessDeniedException("Only volunteers with active REGISTERED or CONFIRMED status may check in.");
            }

            UnitMembership activeMembership = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId())
                .orElseThrow(() -> new AccessDeniedException("Volunteer must be enrolled in an active NSS unit."));

            if (!session.getEvent().isUnitEligible(activeMembership.getUnit().getUnitId())) {
                throw new AccessDeniedException("Your active NSS Unit (" + activeMembership.getUnit().getUnitNumber() + ") is not eligible to participate in this event.");
            }

            // Database authoritative check: existing record
            if (recordRepository.existsBySession_SessionIdAndVolunteer_VolunteerId(session.getSessionId(), volunteer.getVolunteerId())) {
                throw new DuplicateAttendanceException();
            }

            // Database authoritative unique insert: uq_session_volunteer_attendance
            AttendanceRecord savedRecord;
            try {
                AttendanceRecord record = new AttendanceRecord(session, volunteer, "QR", "PRESENT");
                savedRecord = recordRepository.saveAndFlush(record);
            } catch (DataIntegrityViolationException dive) {
                throw new DuplicateAttendanceException();
            }

            creditServiceHours(session.getEvent(), volunteer, savedRecord, null);

            auditService.logEvent(
                principal.getUsername(),
                "ATTENDANCE_CHECKIN_QR",
                "ATTENDANCE_RECORD",
                savedRecord.getAttendanceId().toString(),
                volunteer.getCollegeId(),
                "NONE",
                "PRESENT",
                "QR check-in for " + session.getEvent().getTitle()
            );

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
            if (redisLockAcquired) {
                try {
                    redisSecurityService.releaseCheckInLock(parsed.sessionId(), volunteer.getVolunteerId());
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Transactional
    public CheckInResponse manualCheckIn(UUID sessionId, ManualCheckInRequest req, UserDetails principal) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        assertManagerForUnit(principal, session.getEvent().getUnit());

        validateSessionAndEvent(session, Instant.now());

        String requestedStatus = req.status() != null ? req.status().trim().toUpperCase() : "";
        if (!List.of("PRESENT", "ABSENT", "EXCUSED").contains(requestedStatus)) {
            throw new IllegalArgumentException("Invalid status: " + req.status() + ". Allowed values: PRESENT, ABSENT, EXCUSED.");
        }

        Volunteer volunteer = volunteerRepository.findById(req.volunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found: " + req.volunteerId()));

        if (!"ACTIVE".equalsIgnoreCase(volunteer.getStatus()) ||
            (volunteer.getUser() != null && !"ACTIVE".equalsIgnoreCase(volunteer.getUser().getStatus()))) {
            throw new AccessDeniedException("Volunteer status must be ACTIVE to record attendance.");
        }

        User manager = currentUser(principal);
        if (volunteer.getUser() != null &&
            (volunteer.getUser().getUserId().equals(manager.getUserId()) ||
             volunteer.getUser().getEmail().equalsIgnoreCase(principal.getUsername()))) {
            throw new AccessDeniedException("Self-check-in via manual attendance is strictly prohibited.");
        }

        EventRegistration registration = registrationRepository
            .findByEvent_EventIdAndVolunteer_VolunteerId(session.getEvent().getEventId(), volunteer.getVolunteerId())
            .orElseThrow(() -> new AccessDeniedException("Volunteer is not registered for this event."));

        if (!"REGISTERED".equals(registration.getStatus()) && !"CONFIRMED".equals(registration.getStatus())) {
            throw new AccessDeniedException("Only volunteers with active REGISTERED or CONFIRMED status may receive attendance.");
        }

        UnitMembership activeMembership = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId())
            .orElseThrow(() -> new AccessDeniedException("Volunteer must be enrolled in an active NSS unit."));

        if (!session.getEvent().isUnitEligible(activeMembership.getUnit().getUnitId())) {
            throw new AccessDeniedException("Volunteer's active NSS Unit (" + activeMembership.getUnit().getUnitNumber() + ") is not eligible to participate in this event.");
        }

        Optional<AttendanceRecord> existing = recordRepository.findBySession_SessionIdAndVolunteer_VolunteerIdWithLock(sessionId, volunteer.getVolunteerId());
        String previousStatus = null;
        AttendanceRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            previousStatus = record.getStatus();
            if (previousStatus.equalsIgnoreCase(requestedStatus)) {
                throw new DuplicateAttendanceException();
            }
            if (req.reason() == null || req.reason().trim().isBlank()) {
                throw new IllegalArgumentException("A valid non-blank audit reason is mandatory when modifying existing attendance status.");
            }
            record.setStatus(requestedStatus);
            record.setCheckInMethod("MANUAL_COORDINATOR");
        } else {
            record = new AttendanceRecord(session, volunteer, "MANUAL_COORDINATOR", requestedStatus);
        }

        AttendanceRecord saved;
        try {
            saved = recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException dive) {
            throw new DuplicateAttendanceException();
        }

        if ("PRESENT".equals(requestedStatus)) {
            creditServiceHours(session.getEvent(), volunteer, saved, manager);
        } else {
            revokeServiceHoursOnDemotion(saved, manager);
        }

        auditService.logEvent(
            principal.getUsername(),
            "ATTENDANCE_MANUAL_" + requestedStatus,
            "ATTENDANCE_RECORD",
            saved.getAttendanceId().toString(),
            volunteer.getCollegeId(),
            previousStatus != null ? previousStatus : "NONE",
            requestedStatus,
            req.reason() != null && !req.reason().isBlank() ? req.reason().trim() : "Manual check-in by supervisor"
        );

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

        // Shared lock on AttendanceRecord serializes correction creation against manual check-in and other corrections
        AttendanceRecord record = recordRepository.findByIdWithLock(attendanceId)
            .orElseThrow(() -> new IllegalArgumentException("Attendance record not found: " + attendanceId));

        assertManagerForUnit(principal, record.getSession().getEvent().getUnit());

        String prevStatus = record.getStatus();
        String newStatus = req.newStatus() != null ? req.newStatus().trim().toUpperCase() : "";
        if (!List.of("PRESENT", "ABSENT", "EXCUSED").contains(newStatus)) {
            throw new IllegalArgumentException("Invalid status. Allowed values: PRESENT, ABSENT, EXCUSED.");
        }
        if (newStatus.equalsIgnoreCase(prevStatus)) {
            throw new IllegalArgumentException("New status must be different from current status (" + prevStatus + ").");
        }

        if (correctionRepository.existsByAttendanceRecord_AttendanceIdAndStatus(attendanceId, "PENDING")) {
            throw new IllegalStateException("A correction request is already pending review for this attendance record.");
        }

        User officer = currentUser(principal);
        AttendanceCorrection correction = new AttendanceCorrection(
            record, officer, prevStatus, newStatus, req.reason().trim()
        );
        AttendanceCorrection savedCorrection = correctionRepository.save(correction);

        auditService.logEvent(
            principal.getUsername(),
            "ATTENDANCE_CORRECTION_REQUESTED",
            "ATTENDANCE_RECORD",
            record.getAttendanceId().toString(),
            record.getVolunteer().getCollegeId(),
            prevStatus,
            newStatus,
            req.reason().trim()
        );

        return toCorrectionResponse(savedCorrection);
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> getPendingCorrections(UserDetails principal) {
        assertOfficerOrAdmin(principal);

        List<AttendanceCorrection> pending = correctionRepository.findByStatusOrderByCorrectedAtDesc("PENDING");
        if (principal != null && !unitSecurity.isGlobalManager(principal)) {
            List<UUID> managedIds = unitSecurity.getManagedUnitIds(principal);
            pending = pending.stream().filter(c -> {
                NssUnit unit = c.getAttendanceRecord().getSession().getEvent().getUnit();
                return unit != null && managedIds.contains(unit.getUnitId());
            }).toList();
        }

        return pending.stream()
            .map(this::toCorrectionResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public CorrectionResponse reviewCorrection(UUID correctionId, edu.college.nss.web.dto.CorrectionReviewRequest req, UserDetails principal) {
        AttendanceCorrection correction = correctionRepository.findByIdWithLock(correctionId)
            .orElseThrow(() -> new IllegalArgumentException("Correction not found: " + correctionId));

        assertOfficerForUnit(principal, correction.getAttendanceRecord().getSession().getEvent().getUnit());

        if (!"PENDING".equalsIgnoreCase(correction.getStatus())) {
            throw new IllegalStateException("Correction is not pending review. Current status: " + correction.getStatus());
        }

        // Shared lock on the associated AttendanceRecord ensures reviewCorrection and manualCheckIn share a common serialization boundary
        UUID attendanceId = correction.getAttendanceRecord().getAttendanceId();
        AttendanceRecord record = recordRepository.findByIdWithLock(attendanceId)
            .orElseThrow(() -> new IllegalArgumentException("Associated attendance record not found: " + attendanceId));

        User officer = currentUser(principal);

        // Self-review prevention:
        // 1. Requester cannot review own correction request
        if (correction.getCorrectedBy() != null && correction.getCorrectedBy().getUserId().equals(officer.getUserId())) {
            throw new AccessDeniedException("Reviewer cannot review an attendance correction they submitted.");
        }
        // 2. Target volunteer cannot review own attendance correction
        Volunteer targetVolunteer = record.getVolunteer();
        if (targetVolunteer != null && targetVolunteer.getUser() != null) {
            if (targetVolunteer.getUser().getUserId().equals(officer.getUserId()) ||
                targetVolunteer.getUser().getEmail().equalsIgnoreCase(principal.getUsername())) {
                throw new AccessDeniedException("Reviewer cannot review an attendance correction for their own record.");
            }
        }

        correction.setReviewedBy(officer);
        correction.setReviewedAt(Instant.now());
        correction.setReviewRemarks(req.remarks());

        if (Boolean.TRUE.equals(req.approved())) {
            correction.setStatus("APPROVED");
            record.setStatus(correction.getNewStatus());
            recordRepository.save(record);
            syncServiceHoursOnCorrection(record, correction.getNewStatus(), officer);
        } else {
            correction.setStatus("REJECTED");
        }

        correction = correctionRepository.save(correction);

        auditService.logEvent(
            principal.getUsername(),
            "ATTENDANCE_CORRECTION_" + correction.getStatus(),
            "ATTENDANCE_RECORD",
            record.getAttendanceId().toString(),
            record.getVolunteer().getCollegeId(),
            correction.getPreviousStatus(),
            correction.getStatus().equals("APPROVED") ? correction.getNewStatus() : correction.getPreviousStatus(),
            req.remarks() != null ? req.remarks() : "Attendance correction " + correction.getStatus()
        );

        if (record.getVolunteer().getUser() != null) {
            notificationService.sendNotification(
                record.getVolunteer().getUser(),
                "Attendance Correction " + correction.getStatus(),
                "Your attendance correction request has been " + correction.getStatus() + (req.remarks() != null ? ": " + req.remarks() : ""),
                "ATTENDANCE",
                "/events/" + record.getSession().getEvent().getEventId()
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
    public List<AttendanceListItem> getList(UUID eventId, UUID sessionId, UserDetails principal) {
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
            return new AttendanceListItem(
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

    private void validateSessionAndEvent(AttendanceSession session, Instant now) {
        if (session.getEvent() == null || session.getEvent().getEventId() == null) {
            throw new IllegalStateException("Attendance session is not associated with a valid event.");
        }
        Event event = eventRepository.findByIdWithReadLock(session.getEvent().getEventId())
            .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + session.getEvent().getEventId()));
        if (!"OPEN".equalsIgnoreCase(event.getStatus())) {
            throw new IllegalStateException("Event is not open for attendance (current status: " + event.getStatus() + "). Attendance cannot be recorded.");
        }
        if (!"OPEN".equalsIgnoreCase(session.getStatus())) {
            throw new AttendanceSessionExpiredException("Attendance session is not open.");
        }
        if (now.isBefore(session.getStartsAt())) {
            throw new AttendanceSessionExpiredException("Attendance session has not started yet.");
        }
        if (!now.isBefore(session.getExpiresAt())) {
            session.expire();
            sessionRepository.save(session);
            throw new AttendanceSessionExpiredException("Attendance session has expired.");
        }
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

        // Lock parent EventRegistration if present to serialize against concurrent claims
        registrationRepository.findByEvent_EventIdAndVolunteer_VolunteerIdWithLock(event.getEventId(), volunteer.getVolunteerId());

        Optional<ServiceHourEntry> existingActive = serviceHourRepository.findByVolunteer_VolunteerIdAndEvent_EventIdAndStatusInWithLock(
            volunteer.getVolunteerId(), event.getEventId(), List.of("PENDING", "APPROVED")
        );

        if (existingActive.isPresent()) {
            ServiceHourEntry entry = existingActive.get();
            if (entry.getAttendanceRecord() == null) {
                if ("PENDING".equalsIgnoreCase(entry.getStatus())) {
                    entry.setStatus("APPROVED");
                    entry.setAttendanceRecord(record);
                    entry.setApprovedBy(approvedBy);
                    String auditTrail = (entry.getEvidenceNote() != null ? entry.getEvidenceNote() + "\n" : "") +
                        "[AUTO-APPROVED on " + Instant.now() + " by " + (approvedBy != null ? approvedBy.getName() : "SYSTEM") + "]: Pending claim linked and approved upon attendance verification";
                    entry.setEvidenceNote(auditTrail);
                    serviceHourRepository.save(entry);
                } else if ("APPROVED".equalsIgnoreCase(entry.getStatus())) {
                    entry.setAttendanceRecord(record);
                    if (approvedBy != null && entry.getApprovedBy() == null) {
                        entry.setApprovedBy(approvedBy);
                    }
                    String auditTrail = (entry.getEvidenceNote() != null ? entry.getEvidenceNote() + "\n" : "") +
                        "[LINKED on " + Instant.now() + " by " + (approvedBy != null ? approvedBy.getName() : "SYSTEM") + "]: Attendance record linked to pre-existing approved service hours";
                    entry.setEvidenceNote(auditTrail);
                    serviceHourRepository.save(entry);
                }
            } else if (!"APPROVED".equals(entry.getStatus())) {
                entry.setStatus("APPROVED");
                entry.setApprovedBy(approvedBy);
                String auditTrail = (entry.getEvidenceNote() != null ? entry.getEvidenceNote() + "\n" : "") +
                    "[RESTORED on " + Instant.now() + " by " + (approvedBy != null ? approvedBy.getName() : "SYSTEM") + "]: Re-approved upon attendance verification";
                entry.setEvidenceNote(auditTrail);
                serviceHourRepository.save(entry);
            }
            return;
        }

        // If no active entry for event, check if this attendance record was previously linked (e.g. revoked/rejected)
        Optional<ServiceHourEntry> existingByAtt = serviceHourRepository.findByAttendanceRecord_AttendanceIdWithLock(record.getAttendanceId());
        if (existingByAtt.isPresent()) {
            ServiceHourEntry entry = existingByAtt.get();
            if (!"APPROVED".equals(entry.getStatus())) {
                entry.setStatus("APPROVED");
                entry.setApprovedBy(approvedBy);
                String auditTrail = (entry.getEvidenceNote() != null ? entry.getEvidenceNote() + "\n" : "") +
                    "[RESTORED on " + Instant.now() + " by " + (approvedBy != null ? approvedBy.getName() : "SYSTEM") + "]: Re-approved upon attendance verification";
                entry.setEvidenceNote(auditTrail);
                serviceHourRepository.save(entry);
            }
            return;
        }

        // Otherwise create new approved entry
        try {
            ServiceHourEntry entry = new ServiceHourEntry(
                volunteer, event, record, hours, "APPROVED", approvedBy, "Verified attendance at " + event.getTitle()
            );
            serviceHourRepository.saveAndFlush(entry);
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            // Concurrent claim won the race - reconcile by locking and updating existing active entry
            ServiceHourEntry concurrentEntry = serviceHourRepository.findByVolunteer_VolunteerIdAndEvent_EventIdAndStatusInWithLock(
                volunteer.getVolunteerId(), event.getEventId(), List.of("PENDING", "APPROVED")
            ).orElseThrow(() -> dive);
            concurrentEntry.setStatus("APPROVED");
            concurrentEntry.setAttendanceRecord(record);
            if (approvedBy != null && concurrentEntry.getApprovedBy() == null) {
                concurrentEntry.setApprovedBy(approvedBy);
            }
            serviceHourRepository.save(concurrentEntry);
        }
    }

    private void revokeServiceHoursOnDemotion(AttendanceRecord record, User revokedBy) {
        serviceHourRepository.findByAttendanceRecord_AttendanceIdWithLock(record.getAttendanceId()).ifPresent(entry -> {
            if ("APPROVED".equals(entry.getStatus())) {
                entry.setStatus("REVOKED");
                entry.setApprovedBy(revokedBy);
                String auditTrail = (entry.getEvidenceNote() != null ? entry.getEvidenceNote() + "\n" : "") +
                    "[REVOKED on " + Instant.now() + " by " + (revokedBy != null ? revokedBy.getName() : "SYSTEM") + "]: Attendance demoted to " + record.getStatus();
                entry.setEvidenceNote(auditTrail);
                serviceHourRepository.save(entry);
            }
        });
    }

    private void syncServiceHoursOnCorrection(AttendanceRecord record, String newStatus, User officer) {
        if ("PRESENT".equalsIgnoreCase(newStatus)) {
            creditServiceHours(record.getSession().getEvent(), record.getVolunteer(), record, officer);
        } else {
            revokeServiceHoursOnDemotion(record, officer);
        }
    }

    private void assertManagerForUnit(UserDetails principal, NssUnit unit) {
        if (hasRole(principal, "ADMIN") || hasRole(principal, "FACULTY_COORDINATOR")) return;
        if (hasRole(principal, "PROGRAMME_OFFICER")) {
            User user = currentUser(principal);
            if (unit.getOfficer() != null && unit.getOfficer().getUserId().equals(user.getUserId())) return;
        }
        if (hasRole(principal, "STUDENT_LEADER")) {
            boolean isMember = membershipRepository.findByVolunteer_User_EmailIgnoreCaseAndUnit_UnitIdAndIsActiveTrue(principal.getUsername(), unit.getUnitId())
                .isPresent();
            if (isMember) return;
        }
        throw new AccessDeniedException("You do not have permission to manage attendance for this NSS unit.");
    }

    private void assertOfficerForUnit(UserDetails principal, NssUnit unit) {
        if (hasRole(principal, "ADMIN") || hasRole(principal, "FACULTY_COORDINATOR")) return;
        if (hasRole(principal, "PROGRAMME_OFFICER")) {
            User user = currentUser(principal);
            if (unit.getOfficer() != null && unit.getOfficer().getUserId().equals(user.getUserId())) return;
        }
        throw new AccessDeniedException("Access denied. Programme Officer or Admin privileges required for this NSS unit.");
    }

    private void assertOfficerOrAdmin(UserDetails principal) {
        if (hasRole(principal, "ADMIN") || hasRole(principal, "FACULTY_COORDINATOR") || hasRole(principal, "PROGRAMME_OFFICER")) {
            return;
        }
        throw new AccessDeniedException("Access denied. Officer or Admin privileges required.");
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
