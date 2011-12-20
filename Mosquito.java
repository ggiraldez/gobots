/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gobots;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.Robot;
import robocode.RobotDeathEvent;
import robocode.RobotStatus;
import robocode.ScannedRobotEvent;
import robocode.StatusEvent;
import robocode.WinEvent;
import robocode.util.Utils;

/**
 * Mosquito - Robot simple.
 * 
 * Molesto y ponsoñoso. A pesar de ser un Robot, exprime al máximo los turnos.
 * Mediante el uso de onStatus() ejecuta órdenes aún cuando está bloqueado en
 * un ahead() o un back() en run().
 * 
 * @author Gustavo Giráldez <gustavo.giraldez@gmail.com>
 */
public class Mosquito extends Robot {

    private static final int AHEAD_MAX = 300;
    private static final int AHEAD_MIN = 60;
    private static final int TARGET_DESIRED_DISTANCE = 200;
    private static final int MAX_POWER_DISTANCE = 200;
    private static final int POWER_DISTANCE_DECAY = 400;
    private static final double POWER_RETALIATION_THRESHOLD = 4.0;
    protected Random rand = new Random();
    
    // el nombre del robot que estamos siguiendo
    private String tracking;

    // signo para el siguiente ahead()
    private int nextMoveSign = 1;
    
    // variables de control dentro del último movimiento
    // estas determinan si debemos cambiar el sentido de marcha
    private boolean collision = false;
    private boolean hitByBullet = false;
    
    // indicador de si estamos dentro del evento onScannedRobot
    private boolean inScannedRobot = false;
    
    private boolean moving = false;
    
    // variables de control de represalia por disparos
    private String lastHitSource;
    private double hitPower = 0;

    // colección de robots enemigos
    private Map<String, BotInfo> enemies;
    
    // signo para el siguiente scan por si perdemos de vista el objetivo
    private int scanSign = -1;

    // centros de gravedad
    private GUtil.GravityCenter centerGC;
    private GUtil.GravityCenter[] cornerGCs;
    
    
    @Override
    public void run() {
        Color wineColor = new Color(112, 44, 75);
        setColors(wineColor, wineColor, wineColor, Color.cyan, Color.white);

        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);

        // inicialización
        enemies = new HashMap<String, BotInfo>();
        
        double h = getBattleFieldHeight();
        double w = getBattleFieldWidth();
        centerGC = new GUtil.GravityCenter(w / 2, h / 2, Math.max(w * .4, h * .4));
        cornerGCs = new GUtil.GravityCenter[4];
        cornerGCs[0] = new GUtil.GravityCenter(0, 0, Math.min(w, h));
        cornerGCs[1] = new GUtil.GravityCenter(0, h, Math.min(w, h));
        cornerGCs[2] = new GUtil.GravityCenter(w, h, Math.min(w, h));
        cornerGCs[3] = new GUtil.GravityCenter(w, 0, Math.min(w, h));

        // loop principal
        while (true) {
            if (getTracking() != null) {
                if (collision || hitByBullet) {
                    nextMoveSign = -1 * nextMoveSign;
                }
                out.println("Moving " + (nextMoveSign > 0 ? "ahead" : "back"));

                moving = true;
                collision = false;
                hitByBullet = false;
                int amount = rand.nextInt(AHEAD_MAX - AHEAD_MIN) + AHEAD_MIN;
                ahead(amount * nextMoveSign);
                moving = false;

            } else {
                out.println("Normal scan");
                turnRadarLeft(45);
            }
        }
    }

    private BotInfo getBotInfo(String name) {
        BotInfo info = enemies.get(name);
        if (info == null) {
            info = new BotInfo(name);
            enemies.put(name, info);
        }
        return info;
    }

    private boolean isBotAlive(String name) {
        BotInfo info = getBotInfo(name);
        return info.isAlive();
    }

    @Override
    public void onHitByBullet(HitByBulletEvent evt) {
        if (lastHitSource != null && lastHitSource.equals(evt.getName())) {
            hitPower += evt.getPower();
            if (hitPower > POWER_RETALIATION_THRESHOLD) {
                setTracking(evt.getName());

                // calcular el radarTurn hacia el nuevo objetivo para setear el
                // scanSign para minimizar el tiempo de scan
                double radarTurn = evt.getBearing() + getHeading() - getRadarHeading();
                radarTurn = GUtil.normalRelativeAngle(radarTurn);
                scanSign = radarTurn < 0 ? -1 : 1;
            }
        } else {
            lastHitSource = evt.getName();
            hitPower = evt.getPower();
        }
        hitByBullet = true;
    }

    @Override
    public void onHitRobot(HitRobotEvent evt) {
        if (evt.isMyFault()) {
            collision = true;
        } else {
            // tomar represalia contra el rammer
            setTracking(evt.getName());

            // calcular el radarTurn hacia el nuevo objetivo para setear el
            // scanSign para minimizar el tiempo de scan
            double radarTurn = evt.getBearing() + getHeading() - getRadarHeading();
            radarTurn = GUtil.normalRelativeAngle(radarTurn);
            scanSign = radarTurn < 0 ? -1 : 1;
        }
    }

    @Override
    public void onHitWall(HitWallEvent evt) {
        collision = true;
    }

    @Override
    public void onRobotDeath(RobotDeathEvent evt) {
        BotInfo info = getBotInfo(evt.getName());
        info.setAlive(false);

        if (getTracking() != null && getTracking().equals(evt.getName())) {
            setTracking(null);
        }
    }

    @Override
    public void onWin(WinEvent evt) {
        turnGunRight(45);
        for (int i = 0; i < 10; i++) {
            turnGunLeft(90);
            turnGunRight(90);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent evt) {
        // registrar la velocidad del bot
        BotInfo info = getBotInfo(evt.getName());
        info.addVelocity(evt.getVelocity());

        // verificar tracking y/o actualizar tracking
        if (getTracking() != null && !evt.getName().equals(tracking)) {
            return;
        }
        if (getTracking() == null) {
            setTracking(evt.getName());
        }

        inScannedRobot = true;

        double velocity = getVelocity();
        double maxRobotTurn = 10 - .75 * Math.abs(velocity);
        double distance = evt.getDistance();
        double x = getX();
        double y = getY();

        double absoluteBearing = getHeading() + evt.getBearing();
        double radarTurn = absoluteBearing - getRadarHeading();
        double gunTurn = absoluteBearing - getGunHeading();

        // calcular el giro del robot usando "centros gravitatorios" en el objetivo,
        // el centro de la arena, y 
        double robotTurn = 0;
        
        GUtil.GravityCenter targetGC = new GUtil.GravityCenter(x, y, absoluteBearing, distance, TARGET_DESIRED_DISTANCE);
        double turnForTarget = targetGC.calculateTurn(x, y, getHeading(), velocity);
        double qt = sigmoid(TARGET_DESIRED_DISTANCE, distance);

        // verificar distancia a las esquinas
        double turnForCorner = 0;
        double qc = 0;
        double cornerDistance = Math.min(getBattleFieldHeight(), getBattleFieldWidth()) / 8;
        for (int i = 0; i < cornerGCs.length; i++) {
            GUtil.GravityCenter cgc = cornerGCs[i];
            double dist = cgc.calculateDistance(x, y);
            if (dist < cornerDistance) {
                // estamos muy cerca de la esquina i
                // calcular los giros para las esquinas i-1 e i+1
                qc = sigmoid(cornerDistance, dist);
                int ni = i < cornerGCs.length - 1 ? i + 1 : 0;
                int pi = i > 0 ? i - 1 : cornerGCs.length - 1;
                double nturn = cornerGCs[ni].calculateTurn(x, y, getHeading(), velocity);
                double pturn = cornerGCs[pi].calculateTurn(x, y, getHeading(), velocity);
                if (Math.abs(nturn) < Math.abs(pturn)) {
                    turnForCorner = nturn;
                } else {
                    turnForCorner = pturn;
                }
                break;
            }
        }

        if (qc > .2 || getOthers() <= 1) {
            robotTurn = qc * turnForCorner + (1 - qc) * turnForTarget;
        } else {
            double turnForCenter = centerGC.calculateTurn(x, y, getHeading(), velocity);
            double dist2Center = centerGC.calculateDistance(x, y);
            double q = sigmoid(centerGC.getDesiredDistance(), dist2Center);
            if (q > qt) {
                robotTurn = (1 - q) * turnForTarget + q * turnForCenter;
            } else {
                robotTurn = qt * turnForTarget + (1 - qt) * turnForCenter;
            }
        }
        
        gunTurn = GUtil.normalRelativeAngle(gunTurn);
        radarTurn = GUtil.normalRelativeAngle(radarTurn);

        // signo de radar turn, para que si con el próximo giro no logramos un 
        // scan del objetivo, el giro en onStatus() sí lo alcance
        scanSign = radarTurn < 0 ? -1 : 1;

        // calcular la potencia de disparo si podemos disparar
        // recalcular gunTurn también usando una técnica lineal simple
        double power = 0.0;
        if (getGunHeat() == 0) {
            power = Math.min(3.0, getEnergy() / 10);
            power = Math.min(power, evt.getEnergy() / 4);

            if (distance > MAX_POWER_DISTANCE) {
                power = (power - .1) * Math.exp((MAX_POWER_DISTANCE - distance) / POWER_DISTANCE_DECAY) + .1;
            }
            power = Math.max(power, .1);

            if (evt.getEnergy() > 0) {
                // gunTurn tiene el ángulo head-on; calcular otro ángulo usando una 
                // técnica lineal
                double bulletSpeed = 20.0 - 3 * power;
                double enemyVelocity = info.getAverageVelocity();
                out.printf("Enemy velocity %.2f\n", enemyVelocity);
                double linearGunTurn = Math.toRadians(gunTurn) + (enemyVelocity * Math.sin(evt.getHeadingRadians() - Math.toRadians(absoluteBearing)) / (bulletSpeed * 1.0));
                linearGunTurn = GUtil.normalRelativeAngle(Math.toDegrees(linearGunTurn));
                gunTurn = linearGunTurn;
            }
        }

        // si podemos disparar, hacerlo lo más pronto posible
        boolean doFire = false;
        if (getGunHeat() == 0 && Math.abs(gunTurn) < 20) {
            doFire = true;
        }

        if (!doFire && velocity == 0) {
            // dar la oportunidad de iniciar el movimiento si estamos trackeando
            // a menos que estemos muy mal orientados
            if (Math.abs(robotTurn) > 2 * maxRobotTurn) {
                // si estamos mal orientados, permitir todo el giro antes de empezar a movernos
                maxRobotTurn = Math.abs(robotTurn);
            } else {
                inScannedRobot = false;
                return;
            }
        }

        // ajustar el radarTurn para un wide lock
        //double arcToScan = Math.min(Math.atan(3 * 36.0 / evt.getDistance()), Math.PI / 4.0);
        //radarTurn += (radarTurn < 0) ? -arcToScan : arcToScan;

        boolean gunForRobot = true;
        if (Math.signum(robotTurn) == Math.signum(gunTurn) && Math.abs(robotTurn) <= Math.abs(gunTurn)) {
            gunForRobot = false;
        }
        setAdjustGunForRobotTurn(gunForRobot);

        boolean radarForGun = true;
        if (Math.signum(gunTurn) == Math.signum(radarTurn)) {
            radarForGun = false;
        }
        setAdjustRadarForGunTurn(radarForGun);

        //out.format("[%04d] radarTurn: %6.2f, gunTurn: %6.2f, robotTurn: %6.2f\n",
        //        getTime(), radarTurn, gunTurn, robotTurn);

        if (!doFire && Math.abs(robotTurn) > maxRobotTurn / 3) {
            if (Math.abs(robotTurn) > maxRobotTurn) {
                robotTurn = maxRobotTurn * Math.signum(robotTurn);
            }
            turnRight(robotTurn);
            if (!gunForRobot) {
                gunTurn -= robotTurn;
                if (!radarForGun) {
                    radarTurn -= robotTurn;
                }
            }
        }

        double targetArc = Math.min(Math.atan(36.0 / distance), Math.PI / 4.0);
        if (!(Utils.isNear(gunTurn, 0.0) || (doFire && Math.abs(gunTurn) < targetArc))) {
            if (Math.abs(gunTurn) > 20) {
                gunTurn = 20 * Math.signum(gunTurn);
            }
            turnGunRight(gunTurn);
            if (!radarForGun) {
                if (Math.abs(gunTurn) > Math.abs(radarTurn)) {
                    radarTurn = 0;
                } else {
                    radarTurn -= gunTurn;
                }
            }
        }
        if (doFire) {
            out.printf("Fire power %.2f\n", power);
            fire(power);
        }

        if (!Utils.isNear(radarTurn, 0.0)) {
            turnRadarRight(radarTurn);
        }

        inScannedRobot = false;
    }


    @Override
    public void onStatus(StatusEvent evt) {
        RobotStatus status = evt.getStatus();

        // si el robot se está moviendo, hacer el scan aquí
        if (Math.abs(status.getVelocity()) > 0 && !inScannedRobot) {
            out.println("scan en onStatus");
            turnRadarRight(scanSign * 45);
        }
    }

    public String getTracking() {
        return tracking;
    }

    public void setTracking(String name) {
        if (isBotAlive(name)) {
            out.println("Now tracking " + name);
            this.tracking = name;
        }
    }

    private static double sigmoid(double optimalD, double d) {
        return 1.0 / (1 + Math.exp(d - optimalD));
    }
}


