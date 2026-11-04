package edu.college.nss.service;

import edu.college.nss.domain.Announcement;
import edu.college.nss.domain.Event;
import edu.college.nss.domain.NssUnit;
import edu.college.nss.repository.AnnouncementRepository;
import edu.college.nss.repository.EventRepository;
import edu.college.nss.repository.NssUnitRepository;
import edu.college.nss.web.dto.PublicDTOs.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PublicService {

    private final EventRepository eventRepository;
    private final NssUnitRepository unitRepository;
    private final AnnouncementRepository announcementRepository;

    public PublicService(EventRepository eventRepository,
                         NssUnitRepository unitRepository,
                         AnnouncementRepository announcementRepository) {
        this.eventRepository = eventRepository;
        this.unitRepository = unitRepository;
        this.announcementRepository = announcementRepository;
    }

    public PublicOverviewResponse getOverview() {
        Instant now = Instant.now();
        long activeDrives = eventRepository.countActiveDrives(now);
        long completedDrives = eventRepository.countCompletedDrives();
        long verifiedUnits = unitRepository.count();
        long publishedNotices = announcementRepository.countActivePublic(now);

        Page<Event> upcomingPage = eventRepository.searchPublic(null, null, PageRequest.of(0, 4, Sort.by("startAt").ascending()));
        List<PublicEventResponse> upcomingDrives = upcomingPage.getContent().stream()
            .map(this::toPublicEventResponse)
            .toList();

        List<PublicAnnouncementResponse> recentNotices = announcementRepository.findActivePublic(now).stream()
            .limit(4)
            .map(this::toPublicAnnouncementResponse)
            .toList();

        return new PublicOverviewResponse(
            activeDrives,
            completedDrives,
            verifiedUnits,
            publishedNotices,
            upcomingDrives,
            recentNotices
        );
    }

    public Page<PublicEventResponse> getEvents(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<Event> events = eventRepository.searchPublic(null, null, PageRequest.of(page, safeSize, Sort.by("startAt").ascending()));
        return events.map(this::toPublicEventResponse);
    }

    public List<PublicUnitResponse> getUnits() {
        return unitRepository.findAll(Sort.by("unitNumber").ascending()).stream()
            .map(u -> new PublicUnitResponse(
                u.getUnitNumber(),
                u.getUnitName(),
                u.getOfficer() != null ? u.getOfficer().getName() : null
            ))
            .toList();
    }

    public List<PublicAnnouncementResponse> getAnnouncements() {
        Instant now = Instant.now();
        return announcementRepository.findActivePublic(now).stream()
            .map(this::toPublicAnnouncementResponse)
            .toList();
    }

    private PublicEventResponse toPublicEventResponse(Event e) {
        return new PublicEventResponse(
            e.getTitle(),
            e.getDescription(),
            e.getEventType(),
            e.getEventScope(),
            e.getVenue(),
            e.getStartAt(),
            e.getEndAt(),
            e.getStatus(),
            e.getUnit() != null ? e.getUnit().getUnitNumber() : null,
            e.getUnit() != null ? e.getUnit().getUnitName() : null
        );
    }

    private PublicAnnouncementResponse toPublicAnnouncementResponse(Announcement a) {
        return new PublicAnnouncementResponse(
            a.getTitle(),
            a.getContent(),
            a.getPriority(),
            a.getPublishedAt(),
            a.getUnit() != null ? a.getUnit().getUnitNumber() : null,
            a.getUnit() != null ? a.getUnit().getUnitName() : null
        );
    }
}

