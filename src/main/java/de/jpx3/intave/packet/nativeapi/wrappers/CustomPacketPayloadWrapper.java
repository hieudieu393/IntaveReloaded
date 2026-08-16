package de.jpx3.intave.packet.nativeapi.wrappers;
public class CustomPacketPayloadWrapper {
  private final MinecraftKey id;
  private final byte[] payload;
  public CustomPacketPayloadWrapper(MinecraftKey id, byte[] payload){this.id=id;this.payload=payload;}
  public CustomPacketPayloadWrapper(byte[] payload, MinecraftKey id){this(id,payload);}
  public MinecraftKey getId(){return id;} public byte[] getPayload(){return payload;} public MinecraftKey getKey(){return id;} public byte[] getData(){return payload;}
}
