import React, { useEffect, useRef, useState } from 'react';
import {
  LiveKitRoom,
  useRoomContext,
  useTracks,
} from '@livekit/components-react';
import {
  RoomEvent,
  Track,
} from 'livekit-client';

interface RemoteControlTabProps {
  roomToken: string;
  serverUrl: string;
}

export const RemoteControlTab: React.FC<RemoteControlTabProps> = ({
  roomToken,
  serverUrl,
}) => {
  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <LiveKitRoom
        token={roomToken}
        serverUrl={serverUrl}
        connect={true}
        data-lk-theme="default"
        style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
      >
        <RemoteScreen />
      </LiveKitRoom>
    </div>
  );
};

const RemoteScreen: React.FC = () => {
  const room = useRoomContext();
  const [isConnecting, setIsConnecting] = useState(true);

  // Find the screen share track
  const tracks = useTracks([Track.Source.ScreenShare]);
  const screenTrackRef = tracks[0];

  // Video element ref
  const videoRef = useRef<HTMLVideoElement>(null);

  // Connection state monitoring
  useEffect(() => {
    const handleConnected = () => setIsConnecting(false);
    const handleDisconnected = () => setIsConnecting(false);

    if (room.state === 'connected') {
      setIsConnecting(false);
    }

    room.on(RoomEvent.Connected, handleConnected);
    room.on(RoomEvent.Disconnected, handleDisconnected);

    return () => {
      room.off(RoomEvent.Connected, handleConnected);
      room.off(RoomEvent.Disconnected, handleDisconnected);
    };
  }, [room]);

  // Attach track to video element
  useEffect(() => {
    const videoEl = videoRef.current;
    if (screenTrackRef && videoEl) {
      const pub = screenTrackRef.publication;
      // Check if track exists and is a video track
      if (pub && pub.track && pub.track.kind === Track.Kind.Video) {
        pub.track.attach(videoEl);
        return () => {
             pub.track?.detach(videoEl);
        };
      }
    }
  }, [screenTrackRef]);


  const sendControlData = async (payload: object, reliable: boolean) => {
    if (!room.localParticipant) return;

    const strPayload = JSON.stringify(payload);
    const encoder = new TextEncoder();
    const data = encoder.encode(strPayload);

    try {
      await room.localParticipant.publishData(
        data,
        { reliable }
      );
    } catch (e) {
      console.error("Failed to send data", e);
    }
  };

  const handlePointerEvent = (
    e: React.PointerEvent<HTMLVideoElement>,
    action: 'DOWN' | 'MOVE' | 'UP'
  ) => {
    if (!videoRef.current) return;

    const videoElement = videoRef.current;
    const rect = videoElement.getBoundingClientRect();
    const videoWidth = videoElement.videoWidth;
    const videoHeight = videoElement.videoHeight;

    if (videoWidth === 0 || videoHeight === 0) return;

    // Calculate the rendered video dimensions within the element (accounting for object-fit: contain)
    const elementRatio = rect.width / rect.height;
    const videoRatio = videoWidth / videoHeight;

    let renderedWidth = rect.width;
    let renderedHeight = rect.height;
    let offsetX = 0;
    let offsetY = 0;

    if (elementRatio > videoRatio) {
      // Element is wider than video -> vertical pillars (black bars on left/right)
      renderedWidth = rect.height * videoRatio;
      offsetX = (rect.width - renderedWidth) / 2;
    } else {
      // Element is taller than video -> horizontal letterboxing (black bars top/bottom)
      renderedHeight = rect.width / videoRatio;
      offsetY = (rect.height - renderedHeight) / 2;
    }

    const clientX = e.clientX - rect.left;
    const clientY = e.clientY - rect.top;

    const x = (clientX - offsetX) / renderedWidth;
    const y = (clientY - offsetY) / renderedHeight;

    // Clamp values to 0-1
    const clampedX = Math.max(0, Math.min(1, x));
    const clampedY = Math.max(0, Math.min(1, y));

    const payload = {
      action,
      x: clampedX,
      y: clampedY,
    };

    // Use reliable for DOWN/UP, lossy for MOVE
    const reliable = action !== 'MOVE';
    sendControlData(payload, reliable);
  };

  if (isConnecting) {
    return (
      <div style={styles.centerContainer}>
        <p>Connecting to room...</p>
      </div>
    );
  }

  if (!screenTrackRef) {
    return (
      <div style={styles.centerContainer}>
        <p>Waiting for screen share...</p>
        <button onClick={() => room.disconnect()} style={styles.button}>
          Disconnect
        </button>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div style={styles.videoContainer}>
        <video
          ref={videoRef}
          style={styles.video}
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture(e.pointerId);
            handlePointerEvent(e, 'DOWN');
          }}
          onPointerMove={(e) => {
              if (e.buttons > 0) {
                  handlePointerEvent(e, 'MOVE');
              }
          }}
          onPointerUp={(e) => {
            e.currentTarget.releasePointerCapture(e.pointerId);
            handlePointerEvent(e, 'UP');
          }}
          // Ensure video plays
          autoPlay
          playsInline
          muted
        />
      </div>
      <div style={styles.controls}>
        <button onClick={() => room.disconnect()} style={styles.button}>
          Disconnect
        </button>
      </div>
    </div>
  );
};

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column' as const,
    height: '100%',
    width: '100%',
    backgroundColor: '#000',
  },
  centerContainer: {
    display: 'flex',
    flexDirection: 'column' as const,
    justifyContent: 'center',
    alignItems: 'center',
    height: '100%',
    width: '100%',
    color: '#fff',
  },
  videoContainer: {
    flex: 1,
    position: 'relative' as const,
    overflow: 'hidden',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
  },
  video: {
    width: '100%',
    height: '100%',
    objectFit: 'contain' as const,
    cursor: 'crosshair',
  },
  controls: {
    padding: '10px',
    backgroundColor: '#222',
    display: 'flex',
    justifyContent: 'flex-end',
  },
  button: {
    padding: '8px 16px',
    backgroundColor: '#e53935',
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
  },
};
