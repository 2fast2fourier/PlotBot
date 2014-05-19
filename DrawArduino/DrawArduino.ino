#include <AccelStepper.h>
#include <AFMotor.h>
#include <Servo.h> 

//#define USE_MOTOR_SHIELD

#define COMMAND_KEY 33
#define COMMAND_CLEAR_BUFFER 34
#define COMMAND_TRANSFER_START 35
#define COMMAND_TRANSFER_END 36
#define COMMAND_EXECUTE 37
#define COMMAND_HALT 38

#define CMD_DRIVE 64
#define CMD_DRIVE_SPS 65
#define CMD_DRIVE_STEPMODE 66
#define CMD_ARM_EXTEND 67
#define CMD_ARM_RETRACT 68

#define STEPMODE_SINGLE 49
#define STEPMODE_DOUBLE 50
#define STEPMODE_INTERLEAVE 51
#define STEPMODE_MICROSTEP 52

#define STATUS_READY 35
#define STATUS_DISABLED 33

#define STATUS_LED_PIN 13

#define STEP_PER_ROTATION_X 200
#define STEP_PER_ROTATION_Y 200

#define BUFFER_SIZE 1024

#define PACKET_SIZE 5

#define ARM_PIN 9
#define ARM_EXTENDED 120
#define ARM_RETRACTED 90
#define ARM_DELAY 1000

#define PIN_X_DIR 2
#define PIN_X_STEP 3
#define PIN_X_ACTIVE 4
#define PIN_Y_DIR 6
#define PIN_Y_STEP 7
#define PIN_Y_ACTIVE 8
 
Servo armServo;

#ifdef USE_MOTOR_SHIELD
AF_Stepper xmotor(STEP_PER_ROTATION_X, 1);
AF_Stepper ymotor(STEP_PER_ROTATION_Y, 2);
#endif

byte buffer[BUFFER_SIZE];
short bufferPos = 0;
short bufferEnd = 0;

byte msg[PACKET_SIZE];
byte bufmsg[PACKET_SIZE];

long length;

unsigned long nextReady;

boolean executing = false;
boolean buffering = false;

boolean armExtended = false;

byte stepperMode = INTERLEAVE;

byte cmd;
short arg1,arg2;

int xSpeed = STEP_PER_ROTATION_X, ySpeed = STEP_PER_ROTATION_Y;

void forwardstepx() {
#ifdef USE_MOTOR_SHIELD
  //motor shield
  xmotor.onestep(FORWARD, stepperMode);
#else
  //Pololu
  digitalWrite(PIN_X_DIR, HIGH);
  digitalWrite(PIN_X_STEP, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_X_STEP, LOW);
#endif
}
void backwardstepx() {  
#ifdef USE_MOTOR_SHIELD
  //motor shield
  xmotor.onestep(BACKWARD, stepperMode);
#else
  //Pololu
  digitalWrite(PIN_X_DIR, LOW);
  digitalWrite(PIN_X_STEP, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_X_STEP, LOW);
#endif
}
void forwardstepy() {  
#ifdef USE_MOTOR_SHIELD
  //motor shield
  ymotor.onestep(FORWARD, stepperMode);
#else
  //Pololu
  digitalWrite(PIN_Y_DIR, HIGH);
  digitalWrite(PIN_Y_STEP, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_Y_STEP, LOW);
#endif
}
void backwardstepy() {  
#ifdef USE_MOTOR_SHIELD
  //motor shield
  ymotor.onestep(BACKWARD, stepperMode);
#else
  //Pololu
  digitalWrite(PIN_Y_DIR, LOW);
  digitalWrite(PIN_Y_STEP, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_Y_STEP, LOW);
#endif
}

boolean buffover(){
  return bufferEnd >= BUFFER_SIZE;
}

void buff(byte* msg, int count){
  int ix = 0;
  for(ix = 0; ix < count; ix++){
    buffer[bufferEnd] = msg[ix];
    bufferEnd++;
  }
}

AccelStepper xAxis(forwardstepx, backwardstepx);
AccelStepper yAxis(forwardstepy, backwardstepy);

void setup()
{
	Serial.begin(9600);
        Serial.println("Starting up...");
        pinMode(STATUS_LED_PIN,OUTPUT);
        
        armServo.attach(ARM_PIN);
        armServo.write(ARM_RETRACTED);
        
        #ifndef USE_MOTOR_SHIELD
        //Pololu stepper controller
        pinMode(PIN_X_STEP,OUTPUT);
        pinMode(PIN_X_DIR,OUTPUT);
        pinMode(PIN_X_ACTIVE, OUTPUT);
        pinMode(PIN_Y_STEP,OUTPUT);
        pinMode(PIN_Y_DIR,OUTPUT);
        pinMode(PIN_Y_ACTIVE, OUTPUT);
        
        digitalWrite(PIN_X_ACTIVE, LOW);
        digitalWrite(PIN_Y_ACTIVE, LOW);
        #endif
        
        delay(500);
        
        nextReady = millis()+1000;
        
        xAxis.setSpeed(STEP_PER_ROTATION_X);
        yAxis.setSpeed(STEP_PER_ROTATION_Y);
        xAxis.setAcceleration(1000);
        yAxis.setAcceleration(1000);
        xAxis.setMaxSpeed(STEP_PER_ROTATION_X);
        yAxis.setMaxSpeed(STEP_PER_ROTATION_Y);
        xAxis.setCurrentPosition(0);
        yAxis.setCurrentPosition(0);
        Serial.println("Startup complete!");
}

void stepperEnable(boolean enabled){
  #ifndef USE_MOTOR_SHIELD
  if(enabled){
    digitalWrite(PIN_X_ACTIVE, HIGH);
    digitalWrite(PIN_Y_ACTIVE, HIGH);
  }else{
    digitalWrite(PIN_X_ACTIVE, LOW);
    digitalWrite(PIN_Y_ACTIVE, LOW);
  }
  #endif
}

void loop()
{
  if (Serial.available() >= PACKET_SIZE) {
    while(Serial.peek() != COMMAND_KEY){
      Serial.read();//Discard mis-aligned data.
    }
    if(Serial.available() >= PACKET_SIZE){
      int len = Serial.readBytes((char*)msg, PACKET_SIZE);
      if (len == PACKET_SIZE && msg[0]==COMMAND_KEY) {
        switch(msg[1]){
          case COMMAND_CLEAR_BUFFER:
            Serial.println("COMMAND_CLEAR_BUFFER");
            bufferPos = 0;
            bufferEnd = 0;
            executing = false;
          break;
          case COMMAND_EXECUTE:
            Serial.println("COMMAND_EXECUTE");
            executing = true;
            digitalWrite(STATUS_LED_PIN, HIGH);
          break;
          case COMMAND_HALT:
            Serial.println("COMMAND_HALT");
            executing = false;
          break;
          case COMMAND_TRANSFER_START:
            Serial.println("COMMAND_TRANSFER_START");
            executing = false;
            buffering = true;
            digitalWrite(STATUS_LED_PIN, HIGH);
            while(buffering == true){
              if(Serial.available() >= PACKET_SIZE){
                len = Serial.readBytes((char*)bufmsg, PACKET_SIZE);
                if (len == PACKET_SIZE) {
                  switch(bufmsg[0]){
                    case COMMAND_TRANSFER_END:
                      buffering = false;
                      break;
                    case CMD_DRIVE_SPS:
                    case CMD_DRIVE:
                      buff(bufmsg, len);
                      break;
                      //don't have any 3 byte commands yet
//                      buff(bufmsg, 3);
//                      break;
                    default:
                      buff(bufmsg, 2);
                      break;
                  }
                }else{
                  Serial.println("ERROR READING BUFFER");
                }
              }
              if(buffover()){
                buffering = false;
                bufferPos = 0;
                bufferEnd = 0;
                  Serial.println("BUFFER OVERFLOW");
              }
            }
            digitalWrite(STATUS_LED_PIN, LOW);
          break;
        }
      }
    }
  }
  if(executing){
    if(bufferPos < bufferEnd){
      digitalWrite(STATUS_LED_PIN, HIGH);
      stepperEnable(true);
      while(bufferPos < bufferEnd){
        cmd = buffer[bufferPos++];
        switch(cmd){
          case CMD_DRIVE:
            arg1 = (short) (buffer[bufferPos] <<8 | buffer[bufferPos+1]);
            arg2 = (short) (buffer[bufferPos+2] <<8 | buffer[bufferPos+3]);
            bufferPos+=4;
            xAxis.move(arg1);
            if(xAxis.distanceToGo() < 0){
              xAxis.setSpeed(-xSpeed);
            }else{
              xAxis.setSpeed(xSpeed);
            }
            yAxis.move(arg2);
            if(yAxis.distanceToGo() < 0){
              yAxis.setSpeed(-ySpeed);
            }else{
              yAxis.setSpeed(ySpeed);
            }
            while(xAxis.distanceToGo() || yAxis.distanceToGo()){
              if(xAxis.distanceToGo() && yAxis.distanceToGo()){
                xAxis.runSpeed();
                yAxis.runSpeed();
              }else if(xAxis.distanceToGo()){
                xAxis.runSpeedToPosition();
              }else if(yAxis.distanceToGo()){
                yAxis.runSpeedToPosition();
              }
            }
            break;
          case CMD_DRIVE_SPS:
            arg1 = (short) (buffer[bufferPos] <<8 | buffer[bufferPos+1]);
            arg2 = (short) (buffer[bufferPos+2] <<8 | buffer[bufferPos+3]);
            bufferPos+=4;
            xSpeed = arg1;
            ySpeed = arg2;
            break;
          case CMD_DRIVE_STEPMODE:
            arg1 = buffer[bufferPos++];
            switch(arg1){
              case STEPMODE_SINGLE:
                stepperMode = SINGLE;
                break;
              case STEPMODE_DOUBLE:
                stepperMode = DOUBLE;
                break;
              case STEPMODE_INTERLEAVE:
                stepperMode = INTERLEAVE;
                break;
              case STEPMODE_MICROSTEP:
                stepperMode = MICROSTEP;
                break;
            }
            break;
          case CMD_ARM_EXTEND:
            armServo.write(ARM_EXTENDED);
            delay(ARM_DELAY);
            bufferPos++;
            break;
          case CMD_ARM_RETRACT:
            armServo.write(ARM_RETRACTED);
            delay(ARM_DELAY);
            bufferPos++;
            break;
          }
        }
      stepperEnable(false);
    }else{
      bufferPos = 0;
      bufferEnd = 0;
      executing = false;
      digitalWrite(STATUS_LED_PIN, LOW);
    }
  }
  if(!executing && !buffering && nextReady < millis()){
    Serial.write(STATUS_READY);
    nextReady = millis()+1000;
  }
}
