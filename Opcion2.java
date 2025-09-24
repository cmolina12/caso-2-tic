import java.io.*;
import java.util.*;

// Página con estado para LRU.
class Pagina {
    final int num;
    boolean cargada = false;
    long ultUso = -1L;

    Pagina(int num) {
        this.num = num;
    }
}

// Una referencia a memoria.
class Ref {
    final int pagina, offset, accion; // accion: 0=r, 1=w
    boolean yaContabilizadaComoFallo = false;

    Ref(int pagina, int offset, int accion) {
        this.pagina = pagina;
        this.offset = offset;
        this.accion = accion;
    }
}

// Proceso de la simulación (lee proc<i>.txt).
class ProcSim {
    final int id;
    int tp, nf, nc, nr, np;

    final ArrayDeque<Ref> refs = new ArrayDeque<>(); // cola de referencias
    final Map<Integer, Long> loaded = new HashMap<>(); // pagina, timestamp (LRU)
    final Map<Integer, Pagina> tabla = new HashMap<>(); // paginas conocidas

    int capacidad = 0; // marcos disponibles (cantidad max de pagina cargadas)

    // metricas
    int hits = 0; // hits de primer intento
    int fallos = 0; // refs que alguna vez fallaron (se cuentan una vez)
    int swaps = 0; 

    ProcSim(int id) {
        this.id = id;
    }

    boolean tieneRefs() {
        return !refs.isEmpty();
    }

    Ref peekRef() {
        return refs.peekFirst();
    }

    void consumeRef() {
        refs.pollFirst();
    }

    void cargarDeArchivo(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String s;
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.isEmpty())
                    continue;

                if (s.startsWith("TP="))
                    tp = Integer.parseInt(s.substring(3));
                else if (s.startsWith("NF="))
                    nf = Integer.parseInt(s.substring(3));
                else if (s.startsWith("NC="))
                    nc = Integer.parseInt(s.substring(3));
                else if (s.startsWith("NR="))
                    nr = Integer.parseInt(s.substring(3));
                else if (s.startsWith("NP="))
                    np = Integer.parseInt(s.substring(3));
                else if (s.contains(",")) {

                    String[] parts = s.split(",");
                    if (parts.length >= 4) {
                        int pagina = Integer.parseInt(parts[1].trim());
                        int offset = Integer.parseInt(parts[2].trim());
                        int accion = parts[3].trim().equalsIgnoreCase("r") ? 0 : 1;
                        refs.add(new Ref(pagina, offset, accion));
                        tabla.computeIfAbsent(pagina, Pagina::new);
                    }
                }
            }
        }
    }
}

// Simulador principal.
class SimCore {
    final int totalFrames;
    final List<ProcSim> procs = new ArrayList<>();
    final ArrayDeque<ProcSim> rr = new ArrayDeque<>();
    long time = 0; // timestamp creciente para LRU

    SimCore(int totalFrames) {
        this.totalFrames = totalFrames;
    }

    void cargarProcesos(int n) throws IOException {
        for (int i = 0; i < n; i++) {
            ProcSim p = new ProcSim(i);
            p.cargarDeArchivo("proc" + i + ".txt");
            procs.add(p);
        }
        // reparto inicial (si no es divisible, los primeros reciben +1)
        int base = (procs.isEmpty() ? 0 : totalFrames / procs.size());
        int rem = (procs.isEmpty() ? 0 : totalFrames % procs.size());
        for (int i = 0; i < procs.size(); i++) {
            procs.get(i).capacidad = base + (i < rem ? 1 : 0);
            rr.addLast(procs.get(i));
        }
    }

    void ejecutar() {
        while (!rr.isEmpty()) {
            ProcSim p = rr.pollFirst();

            if (!p.tieneRefs()) { // terminoo
                reasignarCapacidad(p);
                continue;
            }

            Ref ref = p.peekRef();
            Pagina pg = p.tabla.get(ref.pagina);

            if (pg.cargada) {
                // HIT: si es primer intento (no fallo antes), cuentalo como hit
                if (!ref.yaContabilizadaComoFallo) {
                    p.hits++;
                }
                pg.ultUso = ++time;
                p.loaded.put(pg.num, pg.ultUso);
                p.consumeRef(); // acceso completado
            } else {
                // FALLO: solo se contabiliza la primera vez que falla esta ref
                if (!ref.yaContabilizadaComoFallo) {
                    p.fallos++;
                    ref.yaContabilizadaComoFallo = true;
                }
                if (p.loaded.size() < p.capacidad) {
                    cargarPagina(p, pg);
                    p.swaps += 1; // carga desde swap sin reemplazo
                    // pierde turno: NO consume ref
                } else if (p.capacidad > 0) {
                    int victim = elegirVictimaLRU(p.loaded);
                    descargarPagina(p, victim);
                    cargarPagina(p, pg);
                    p.swaps += 1; // reemplazo (sacar + meter)
                    // pierde turno: NO consume ref
                } else {
                    // capacidad 0: no puede hacer nada este turno
                }
            }

            if (p.tieneRefs())
                rr.addLast(p); // sigue en cola si tiene pendientes
        }
    }

    private void cargarPagina(ProcSim p, Pagina pg) {
        pg.cargada = true;
        pg.ultUso = ++time;
        p.loaded.put(pg.num, pg.ultUso);
    }

    private void descargarPagina(ProcSim p, int paginaVictima) {
        p.loaded.remove(paginaVictima);
        Pagina v = p.tabla.get(paginaVictima);
        if (v != null)
            v.cargada = false;
    }

    private int elegirVictimaLRU(Map<Integer, Long> loaded) {
        int vict = -1;
        long best = Long.MAX_VALUE;
        for (Map.Entry<Integer, Long> e : loaded.entrySet()) {
            if (e.getValue() < best) {
                best = e.getValue();
                vict = e.getKey();
            }
        }
        return vict;
    }

    private void reasignarCapacidad(ProcSim fin) {
        if (fin.capacidad == 0)
            return;
        // elige, entre los que siguen vivos, el de más fallos
        ProcSim target = null;
        for (ProcSim p : procs) {
            if (p != fin && p.tieneRefs()) {
                if (target == null || p.fallos > target.fallos)
                    target = p;
            }
        }
        if (target != null)
            target.capacidad += fin.capacidad;
        fin.capacidad = 0;
        // limpieza
        for (Pagina pg : fin.tabla.values())
            pg.cargada = false;
        fin.loaded.clear();
    }

    void imprimirResultados() {
        for (ProcSim p : procs) {
            int total = p.nr; // NR viene del archivo
            int hitsReales = total - p.fallos; // por definición (cada ref cuenta 1 vez)
            double tasaFallos = total == 0 ? 0 : (double) p.fallos / total;
            double tasaExito = total == 0 ? 0 : (double) hitsReales / total;

            System.out.println("Proceso: " + p.id);
            System.out.println("- Num referencias: " + total);
            System.out.println("- Fallas: " + p.fallos);
            System.out.println("- Hits: " + hitsReales);
            System.out.println("- SWAP: " + p.swaps);
            System.out.println("- Tasa fallos: " + String.format("%.4f", tasaFallos));
            System.out.println("- Tasa éxito: " + String.format("%.4f", tasaExito));
            System.out.println();
        }
    }
}

/** MAIN solo para Opción 2 (sim). */
public class Opcion2 {
    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            System.out.println("Uso: java SimuladorSim <numProcesos> <marcosTotales>");
            return;
        }
        int n = Integer.parseInt(args[0]);
        int frames = Integer.parseInt(args[1]);
        SimCore core = new SimCore(frames);
        core.cargarProcesos(n); // lee proc0.txt..proc{n-1}.txt
        core.ejecutar();
        core.imprimirResultados();
    }
}
