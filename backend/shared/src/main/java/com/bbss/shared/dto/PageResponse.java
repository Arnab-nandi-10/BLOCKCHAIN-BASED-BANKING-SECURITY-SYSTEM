package com.bbss.shared.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response wrapper that adapts Spring Data's {@link Page}
 * into a serialisable DTO suitable for REST API consumers.
 *
 * <p>Controllers should return this type (wrapped in {@link ApiResponse}) for
 * every endpoint that returns a list of resources, ensuring a consistent
 * pagination envelope across all Civic Savings services:
 *
 * <pre>{@code
 * Page<TransactionDto> page = transactionService.findAll(tenantId, pageable);
 * return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
 * }</pre>
 *
 * <p>The JSON representation looks like:
 * <pre>{@code
 * {
 *   "content":       [...],
 *   "page":          0,
 *   "size":          20,
 *   "totalElements": 157,
 *   "totalPages":    8,
 *   "last":          false
 * }
 * }</pre>
 *
 * @param <T> type of elements in the page
 */
@Getter
@Builder
public final class PageResponse<T> {

    /**
     * The items on the current page.
     * Never {@code null}; will be an empty list when no data matches.
     */
    private final List<T> content;

    /**
     * Zero-based page index that was requested (mirrors
     * {@link org.springframework.data.domain.Pageable#getPageNumber()}).
     */
    private final int page;

    /**
     * Maximum number of elements per page that was requested (mirrors
     * {@link org.springframework.data.domain.Pageable#getPageSize()}).
     */
    private final int size;

    /**
     * Total number of elements available across all pages.
     * Used by clients to compute total page count and to render pagination controls.
     */
    private final long totalElements;

    /**
     * Total number of pages given the current {@code size}.
     * Equivalent to {@code Math.ceil((double) totalElements / size)}.
     */
    private final int totalPages;

    /**
     * {@code true} if this is the last available page, {@code false} otherwise.
     * Clients can use this flag to decide whether a "next page" button should
     * be active without needing to compute it from {@code page} and {@code totalPages}.
     */
    private final boolean last;

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Constructs a {@code PageResponse} from a Spring Data {@link Page}.
     *
     * <p>This is the canonical way to build a {@code PageResponse} inside a
     * service or controller layer:
     * <pre>{@code
     * PageResponse<AccountDto> response = PageResponse.from(accountPage);
     * }</pre>
     *
     * @param <T>  element type
     * @param page the Spring Data page; must not be {@code null}
     * @return an equivalent {@code PageResponse}
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
