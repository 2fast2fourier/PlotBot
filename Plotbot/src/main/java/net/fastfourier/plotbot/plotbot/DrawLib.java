package net.fastfourier.plotbot.plotbot;

import android.graphics.Point;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by matthewshepard on 5/18/14.
 */
public class DrawLib {
    private static final int STEP_SPEED = 600;
    private static final int CENTER = (int) ((DrawView.PLOT_SIZE*DrawView.POINT_MULITPLIER)/2);

    //Commands
    private static final byte COMMAND_KEY = 33;
    private static final byte COMMAND_CLEAR_BUFFER = 34;
    private static final byte COMMAND_TRANSFER_START = 35;
    private static final byte COMMAND_TRANSFER_END = 36;
    private static final byte COMMAND_EXECUTE = 37;
    private static final byte COMMAND_HALT = 38;

    private static final byte CMD_DRIVE = 64;
    private static final byte CMD_DRIVE_SPS = 65;
    private static final byte CMD_DRIVE_STEPMODE = 66;
    private static final byte CMD_ARM_EXTEND = 67;
    private static final byte CMD_ARM_RETRACT = 68;

    private static final byte FILL_ZERO = 0;

    public static void generateDrawScript(ByteBuffer buff, DrawingActivity.DrawData data){
        writeCommand(buff, COMMAND_TRANSFER_START);
        //Write draw data
        //test movement
        writeData(buff, CMD_DRIVE, (short) 100, (short) 100);
        writeData(buff, CMD_DRIVE, (short) -100, (short) -100);
        //draw lines
        int cx = CENTER, cy = CENTER, dx, dy;
        for(Point[] line : data.lines){
            if(line.length > 0){
                writeDrive(buff, (short) (cx - line[0].x), (short) (cy - line[0].y));
                cx = line[0].x;
                cy = line[0].y;
                writeData(buff, CMD_ARM_EXTEND);
                for(int ix=1;ix<line.length;ix++){
                    dx = line[ix].x;
                    dy = line[ix].y;
                    writeDrive(buff, (short) (cx - dx), (short) (cy - dy));
                    cx = dx;
                    cy = dy;
                }
                writeData(buff, CMD_ARM_RETRACT);
            }
        }
        writeDrive(buff, (short) (cx - CENTER), (short) (cy - CENTER));

        //End draw data
        writeData(buff, COMMAND_TRANSFER_END);
        writeCommand(buff, COMMAND_EXECUTE);
    }

    private static void writeDrive(ByteBuffer buff, short dx, short dy){
        dy = (short) -dy;
        short sx = (short) (dx+dy), sy = (short) (dx - dy);
        float td = Math.abs(sx)+Math.abs(sy);
        buff.put(CMD_DRIVE_SPS);
        if(sx == 0 || sy == 0){
            buff.putShort((short) STEP_SPEED);
            buff.putShort((short) STEP_SPEED);
        }else{
            short spsx = (short) (Math.abs(sx)/td*STEP_SPEED);
            short spsy = (short) (Math.abs(sy)/td*STEP_SPEED);
            buff.putShort(spsx);
            buff.putShort(spsy);
            Log.e("writeDrive", "Step: "+sx+" - "+sy+" : "+spsx+" - "+spsy+"   ---   "+td);
        }
        buff.put(CMD_DRIVE);
        buff.putShort(sx);
        buff.putShort(sy);
    }

    private static void writeCommand(ByteBuffer buff, byte command){
        buff.put(COMMAND_KEY);
        buff.put(command);
        buff.put(FILL_ZERO);
        buff.put(FILL_ZERO);
        buff.put(FILL_ZERO);
    }

    private static void writeData(ByteBuffer buff, byte command, short data1, short data2){
        buff.put(command);
        buff.putShort(data1);
        buff.putShort(data2);
    }

    private static void writeData(ByteBuffer buff, byte command, short data){
        buff.put(command);
        buff.putShort(data);
        buff.put(FILL_ZERO);
        buff.put(FILL_ZERO);
    }

    private static void writeData(ByteBuffer buff, byte command){
        buff.put(command);
        buff.put(FILL_ZERO);
        buff.put(FILL_ZERO);
        buff.put(FILL_ZERO);
        buff.put(FILL_ZERO);
    }
}
