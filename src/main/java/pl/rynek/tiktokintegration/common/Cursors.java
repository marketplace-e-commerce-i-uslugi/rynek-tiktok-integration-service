package pl.rynek.tiktokintegration.common;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Kursor to przesunięcie na liście; pobieramy o jeden element więcej, żeby wiedzieć, czy jest następna strona. */
public final class Cursors {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private Cursors() {
    }

    public record Page<T>(List<T> items, String nextCursor) {
    }

    public static int offset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(cursor);
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // niżej: 400
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy kursor.");
    }

    public static int limit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit musi wynosić od 1 do " + MAX_LIMIT + ".");
        }
        return limit;
    }

    /** {@code fetch} dostaje stronę o rozmiarze {@code limit + 1}; {@code map} zamienia encje na widoki (jedno wywołanie na stronę). */
    public static <S, T> Page<T> page(String cursor, Integer limit, BiFunction<Pageable, Integer, List<S>> fetch, Function<List<S>, List<T>> map) {
        int offset = offset(cursor);
        int size = limit(limit);
        List<S> rows = fetch.apply(new OffsetPageable(offset, size + 1), size + 1);
        boolean more = rows.size() > size;
        List<S> shown = more ? rows.subList(0, size) : rows;
        return new Page<>(map.apply(shown), more ? String.valueOf(offset + size) : null);
    }

    /** Strona od dowolnego przesunięcia (nie tylko wielokrotności rozmiaru). */
    private static final class OffsetPageable extends PageRequest {
        private final long offset;

        OffsetPageable(int offset, int size) {
            super(0, size, org.springframework.data.domain.Sort.unsorted());
            this.offset = offset;
        }

        @Override
        public long getOffset() {
            return offset;
        }
    }
}
