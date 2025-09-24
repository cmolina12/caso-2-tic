import java.util.Scanner;

public class Launcher {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Launcher Simulador ===");
        System.out.println("1) Generar proc*.txt (Opcion 1)");
        System.out.println("2) Simular (Opcion 2)");
        System.out.print("Elige opcion: ");
        String opcion = sc.nextLine().trim();

        try {
            if (opcion.equals("1")) {
                System.out.print("Ruta del archivo config (ej. config.txt): ");
                String cfg = sc.nextLine().trim();
                Opcion1.main(new String[]{cfg});
            } else if (opcion.equals("2")) {
                System.out.print("Numero de procesos: ");
                int n = Integer.parseInt(sc.nextLine().trim());
                System.out.print("Marcos totales: ");
                int frames = Integer.parseInt(sc.nextLine().trim());
                Opcion2.main(new String[]{String.valueOf(n), String.valueOf(frames)});
            } else {
                System.out.println("Opcion no valida.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        sc.close();
    }
}
