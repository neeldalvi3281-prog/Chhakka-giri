export interface TacticalMessage {
  id: string;
  type: 'BROADCAST' | 'DIRECT' | 'ALERT_CRITICAL' | 'SYSTEM_NOTICE';
  channel: string;
  senderHandle: string;
  senderId: string;
  text: string;
  timestamp: number;
  encryption: string;
  isOutgoing: boolean;
  status: 'QUEUED' | 'RELAYING' | 'SENT' | 'DELIVERED' | 'FAILED';
  ttl: number;
}

export interface TacticalMeshPeer {
  endpointId: string;
  callSign: string;
  nodeId: string;
  rssi: number;
  batteryPercent: number;
}
