/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gobots;

/**
 *
 * @author gustavo
 */
public class GUtil {

    public static double normalRelativeAngle(double degrees) {
        double result = degrees;

        while (result > 180 || result < -180) {
            if (result > 180) {
                result -= 360;
            }
            if (result < -180) {
                result += 360;
            }
        }
        return result;
    }

    public static double normalAbsoluteAngle(double degrees) {
        double result = degrees;
        while (result >= 360 || result < 0) {
            if (result >= 360) {
                result -= 360;
            }
            if (result < 0) {
                result += 360;
            }
        }
        return result;
    }

    public static double calculateBearingToXY(double sourceX, double sourceY,
            double sourceHeading, double targetX, double targetY) {
        return normalRelativeAngle(
                Math.toDegrees(Math.atan2((targetX - sourceX), (targetY - sourceY))) -
                sourceHeading);
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Clase para calcular un promedio móvil
     */
    public static class MovingAverage {

        private double[] data;
        private int samples;
        private int nextSample;
        private double sum;

        public MovingAverage(int samples) {
            this.data = new double[samples];
            this.nextSample = 0;
            this.sum = 0;
            this.samples = 0;
            for (int i = 0; i < data.length; i++) {
                data[i] = 0;
            }
        }

        public void addSample(double value) {
            sum = sum + value - data[nextSample];
            data[nextSample] = value;
            if (samples < data.length) {
                samples++;
            }
            nextSample = (nextSample + 1) % data.length;
        }

        public double getAverage() {
            return sum / samples;
        }

        public double getSum() {
            return sum;
        }
    }

    /**
     * Representación de un centro gravitatorio
     */
    public static class GravityCenter {
        private static final double APROACH_ATTACK_ANGLE = 10;
        private static final double FLEE_ANGLE = 10;

        private double centerX;
        private double centerY;
        private double desiredDistance;

        public GravityCenter(double centerX, double centerY, double desiredDistance) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.desiredDistance = desiredDistance;
        }

        public GravityCenter(double x, double y, double bearing, double distance, double desiredDistance) {
            double bearingRadians = Math.toRadians(bearing);
            this.centerX = x + distance * Math.sin(bearingRadians);
            this.centerY = y + distance * Math.cos(bearingRadians);
            this.desiredDistance = desiredDistance;
        }
        
        public double getCenterX() {
            return centerX;
        }

        public double getCenterY() {
            return centerY;
        }

        public double getDesiredDistance() {
            return desiredDistance;
        }

        public double calculateTurn(double x, double y, double heading, double velocity) {
            // calcular el ángulo de giro del robot menor para ponernos 
            // perpendiculares al objetivo
            double turn;
            double bearing = calculateBearingToXY(x, y, heading, centerX, centerY);
            double rt1 = GUtil.normalRelativeAngle(bearing - 90);
            double rt2 = GUtil.normalRelativeAngle(bearing + 90);
            if (Math.abs(rt1) < Math.abs(rt2)) {
                turn = rt1;
            } else {
                turn = rt2;
            }

            // corregir el ángulo de giro si necesitamos alejarnos o acercarnos al objetivo
            double distance = distance(x, y, centerX, centerY);
            if (distance > desiredDistance * 1.2) {
                // corregir robotTurn para intentar acercarnos al objetivo
                turn += APROACH_ATTACK_ANGLE * Math.signum(velocity) * Math.signum(bearing);
            } else if (distance < desiredDistance * 0.8) {
                // corregir robotTurn para intentar alejarnos del objetivo
                turn -= FLEE_ANGLE * Math.signum(velocity) * Math.signum(bearing);
            }
            turn = normalRelativeAngle(turn);
            return turn;
        }
        
        public double calculateDistance(double x, double y) {
            return distance(x, y, centerX, centerY);
        }
        
    }

}
