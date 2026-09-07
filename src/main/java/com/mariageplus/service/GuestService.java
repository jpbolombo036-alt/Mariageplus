package com.mariageplus.service;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.guest.CreateGuestRequest;
import com.mariageplus.dto.guest.GuestImportError;
import com.mariageplus.dto.guest.GuestImportResponse;
import com.mariageplus.dto.guest.GuestResponse;
import com.mariageplus.dto.guest.UpdateGuestRequest;
import com.mariageplus.entity.Guest;
import com.mariageplus.entity.GuestCategory;
import com.mariageplus.entity.Event;
import com.mariageplus.exception.ConflictException;
import com.mariageplus.exception.ResourceNotFoundException;
import com.mariageplus.mapper.GuestMapper;
import com.mariageplus.repository.GuestRepository;
import com.mariageplus.repository.GuestCategoryRepository;
import com.mariageplus.security.SecurityUtils;
import com.mariageplus.util.CsvParser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module invités. L'accès est conditionné par le mariage parent + la permission.
 * La catégorie d'un invité doit appartenir au MÊME mariage.
 */
@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;
    private final GuestCategoryRepository guestCategoryRepository;
    private final GuestMapper guestMapper;
    private final EventService eventService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional
    public GuestResponse create(Long weddingId, CreateGuestRequest request) {
        securityUtils.assertPermission("GUEST_CREATE");
        Event event = eventService.loadInOrgScope(weddingId);
        validateCategoryBelongsToWedding(weddingId, request.getCategoryId());
        assertEmailUnique(weddingId, request.getEmail());

        Guest guest = Guest.builder()
                .weddingId(weddingId)
                .categoryId(request.getCategoryId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .allowedCompanions(request.getAllowedCompanions() == null ? 0 : request.getAllowedCompanions())
                .notes(request.getNotes())
                .active(true)
                .build();
        Guest saved = guestRepository.save(guest);
        auditService.record("GUEST_CREATE", saved.getId(), "Guest",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Création de l'invité '" + saved.getFirstName() + " " + saved.getLastName() + "'");
        return guestMapper.toResponse(saved);
    }

    public PageResponse<GuestResponse> list(Long weddingId, int page, int size, String sortBy, String sortDir) {
        securityUtils.assertPermission("GUEST_VIEW");
        eventService.loadInOrgScope(weddingId);
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Guest> guestPage = guestRepository.findByWeddingId(weddingId, pageable);
        List<GuestResponse> content = guestPage.getContent().stream()
                .map(guestMapper::toResponse).collect(Collectors.toList());
        return PageResponse.of(content, guestPage);
    }

    public GuestResponse getById(Long weddingId, Long guestId) {
        securityUtils.assertPermission("GUEST_VIEW");
        eventService.loadInOrgScope(weddingId);
        return guestMapper.toResponse(loadGuest(weddingId, guestId));
    }
    @Transactional
    public GuestResponse update(Long weddingId, Long guestId, UpdateGuestRequest request) {
        securityUtils.assertPermission("GUEST_UPDATE");
        Event event = eventService.loadInOrgScope(weddingId);
        Guest guest = loadGuest(weddingId, guestId);

        if (request.getCategoryId() != null && !request.getCategoryId().equals(guest.getCategoryId())) {
            validateCategoryBelongsToWedding(weddingId, request.getCategoryId());
            guest.setCategoryId(request.getCategoryId());
        }
        if (request.getEmail() != null && !request.getEmail().equals(guest.getEmail())) {
            assertEmailUnique(weddingId, request.getEmail());
            guest.setEmail(request.getEmail());
        }
        if (request.getFirstName() != null) guest.setFirstName(request.getFirstName());
        if (request.getLastName() != null) guest.setLastName(request.getLastName());
        if (request.getPhone() != null) guest.setPhone(request.getPhone());
        if (request.getAddress() != null) guest.setAddress(request.getAddress());
        if (request.getAllowedCompanions() != null) guest.setAllowedCompanions(request.getAllowedCompanions());
        if (request.getNotes() != null) guest.setNotes(request.getNotes());
        if (request.getActive() != null) guest.setActive(request.getActive());

        Guest saved = guestRepository.save(guest);
        auditService.record("GUEST_UPDATE", saved.getId(), "Guest",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Modification de l'invité '" + saved.getFirstName() + " " + saved.getLastName() + "'");
        return guestMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long weddingId, Long guestId) {
        securityUtils.assertPermission("GUEST_DELETE");
        eventService.loadInOrgScope(weddingId);
        Guest guest = loadGuest(weddingId, guestId);
        guest.softDelete();
        guestRepository.save(guest);
    }

    /**
     * Import CSV ou Excel (.xlsx/.xls). Les lignes invalides sont rapportées ; les autres sont créées.
     * Colonnes : firstName, lastName, email, phone, address, allowedCompanions,
     * categoryName, notes. Catégorie inconnue → invité créé sans catégorie.
     */
    public GuestImportResponse importCsv(Long weddingId, MultipartFile file) {
        securityUtils.assertPermission("GUEST_IMPORT");
        Event event = eventService.loadInOrgScope(weddingId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier requis (CSV ou Excel .xlsx)");
        }

        List<List<String>> table = isExcelFile(file) ? readExcelRows(file) : readCsvRows(file);
        if (table.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }

        Map<String, Integer> columns = headerMap(table.get(0));
        if (!columns.containsKey("firstname") || !columns.containsKey("lastname")) {
            throw new IllegalArgumentException("En-tête invalide : les colonnes firstName et lastName sont obligatoires");
        }

        Map<String, Long> categoriesByName = guestCategoryRepository.findByWeddingId(weddingId).stream()
                .filter(c -> StringUtils.hasText(c.getName()))
                .collect(Collectors.toMap(
                        c -> c.getName().trim().toLowerCase(Locale.ROOT),
                        GuestCategory::getId,
                        (a, b) -> a));

        Set<String> emailsInFile = new HashSet<>();
        List<GuestImportError> errors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        for (int i = 1; i < table.size(); i++) {
            int lineNumber = i + 1;
            List<String> fields = table.get(i);
            if (fields == null || fields.stream().allMatch(f -> !StringUtils.hasText(f))) {
                skipped++;
                continue;
            }
            try {
                Guest guest = parseGuestRow(weddingId, fields, columns, categoriesByName, emailsInFile);
                guestRepository.save(guest);
                imported++;
            } catch (IllegalArgumentException | ConflictException ex) {
                errors.add(GuestImportError.builder().line(lineNumber).message(ex.getMessage()).build());
            }
        }

        auditService.record("GUEST_IMPORT", weddingId, "Wedding",
                securityUtils.getCurrentUserId(), event.getOrganizationId(),
                "Import : " + imported + " importé(s), " + skipped + " ignoré(s), " + errors.size() + " erreur(s)");
        return GuestImportResponse.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    private boolean isExcelFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    /** Lit un CSV en tableau de lignes (ligne 0 = en-tête, délimiteur détecté automatiquement). */
    private List<List<String>> readCsvRows(MultipartFile file) {
        List<String> lines = readLines(file);
        List<List<String>> table = new ArrayList<>(lines.size());
        if (!lines.isEmpty()) {
            char delimiter = CsvParser.detectDelimiter(lines.get(0));
            for (String line : lines) {
                table.add(CsvParser.parseLine(line, delimiter));
            }
        }
        return table;
    }

    /** Lit un classeur Excel (.xlsx/.xls) en tableau de lignes texte (ligne 0 = en-tête). */
    private List<List<String>> readExcelRows(MultipartFile file) {
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            List<List<String>> table = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                int last = Math.max(row.getLastCellNum(), 0);
                for (int c = 0; c < last; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    cells.add(formatter.formatCellValue(cell).trim());
                }
                table.add(cells);
            }
            return table;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Impossible de lire le fichier Excel (.xlsx/.xls attendu)");
        }
    }

    private List<String> readLines(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    line = CsvParser.stripBom(line);
                    first = false;
                }
                lines.add(line);
            }
            return lines;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Impossible de lire le fichier CSV");
        }
    }

    private Map<String, Integer> headerMap(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headers.get(i) == null ? "" : headers.get(i).trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(key)) {
                columns.put(key, i);
            }
        }
        return columns;
    }

    private Guest parseGuestRow(Long weddingId, List<String> fields, Map<String, Integer> columns,
                                Map<String, Long> categoriesByName, Set<String> emailsInFile) {
        String firstName = field(fields, columns, "firstname");
        String lastName = field(fields, columns, "lastname");
        if (!StringUtils.hasText(firstName)) {
            throw new IllegalArgumentException("Le prénom est requis");
        }
        if (!StringUtils.hasText(lastName)) {
            throw new IllegalArgumentException("Le nom est requis");
        }
        if (firstName.length() > 100 || lastName.length() > 100) {
            throw new IllegalArgumentException("Prénom ou nom trop long (100 caractères max)");
        }

        String email = emptyToNull(field(fields, columns, "email"));
        if (email != null) {
            if (email.length() > 150 || !email.contains("@") || email.contains(" ")) {
                throw new IllegalArgumentException("Email invalide");
            }
            String emailKey = email.toLowerCase(Locale.ROOT);
            if (!emailsInFile.add(emailKey) || guestRepository.existsByEmailAndWeddingId(email, weddingId)) {
                throw new ConflictException("Email déjà utilisé pour ce mariage");
            }
        }

        String phone = emptyToNull(field(fields, columns, "phone"));
        if (phone != null && phone.length() > 20) {
            throw new IllegalArgumentException("Téléphone trop long (20 caractères max)");
        }
        String address = emptyToNull(field(fields, columns, "address"));
        if (address != null && address.length() > 255) {
            throw new IllegalArgumentException("Adresse trop longue (255 caractères max)");
        }
        String notes = emptyToNull(field(fields, columns, "notes"));
        if (notes != null && notes.length() > 1000) {
            throw new IllegalArgumentException("Notes trop longues (1000 caractères max)");
        }

        Integer companions = parseCompanions(field(fields, columns, "allowedcompanions"));
        String categoryName = emptyToNull(field(fields, columns, "categoryname"));
        Long categoryId = null;
        if (categoryName != null) {
            categoryId = categoriesByName.get(categoryName.toLowerCase(Locale.ROOT));
        }

        return Guest.builder()
                .weddingId(weddingId)
                .categoryId(categoryId)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .email(email)
                .address(address)
                .allowedCompanions(companions)
                .notes(notes)
                .active(true)
                .build();
    }

    private Integer parseCompanions(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new IllegalArgumentException("Le nombre d'accompagnants ne peut pas être négatif");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Nombre d'accompagnants invalide");
        }
    }

    private String field(List<String> fields, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index >= fields.size()) {
            return null;
        }
        return fields.get(index);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Guest loadGuest(Long weddingId, Long guestId) {
        return guestRepository.findByIdAndWeddingId(guestId, weddingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invité non trouvé avec l'ID: " + guestId + " pour le mariage " + weddingId));
    }

    /**
     * Une catégorie ne peut être utilisée que si elle appartient au même mariage.
     */
    private void validateCategoryBelongsToWedding(Long weddingId, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (!guestCategoryRepository.existsByIdAndWeddingId(categoryId, weddingId)) {
            throw new IllegalArgumentException("La catégorie n'appartient pas à ce mariage");
        }
    }

    private void assertEmailUnique(Long weddingId, String email) {
        if (StringUtils.hasText(email) && guestRepository.existsByEmailAndWeddingId(email, weddingId)) {
            throw new ConflictException("Email déjà utilisé pour ce mariage");
        }
    }
}