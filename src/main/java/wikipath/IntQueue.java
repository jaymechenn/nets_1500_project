package wikipath;

/**
 * Used FIFO queue backed by a "growable circular buffer"(searched this up).
 * Its used in BFS to avoid the "autoboxing overhead of {@code ArrayDeque<Integer>}"
 * when traversing graphs with  many millions of nodes.
 */
final class IntQueue {

    private int[] buf;
    private int head;
    private int tail;
    private int size;

    IntQueue() {
        this(1024);
    }

    IntQueue(int initialCapacity) {
        this.buf = new int[Math.max(16, initialCapacity)];
    }

    void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void add(int v) {
        if (size == buf.length) {
            grow();
        }
        buf[tail] = v;
        tail = (tail + 1) % buf.length;
        size++;
    }

    int poll() {
        if (size == 0) {
            throw new IllegalStateException("queue is empty");
        }
        int v = buf[head];
        head = (head + 1) % buf.length;
        size--;
        return v;
    }

    private void grow() {
        int newCap = buf.length * 2;
        int[] nb = new int[newCap];
        // Unroll the circular layout into a linear one.
        if (head < tail) {
            System.arraycopy(buf, head, nb, 0, size);
        } else {
            int firstPart = buf.length - head;
            System.arraycopy(buf, head, nb, 0, firstPart);
            System.arraycopy(buf, 0, nb, firstPart, tail);
        }
        buf = nb;
        head = 0;
        tail = size;
    }
}
