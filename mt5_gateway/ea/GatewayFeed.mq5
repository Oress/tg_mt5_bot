// Quote notifications for the local gateway. No trading or credentials in this EA.
#property strict
#property version "1.00"

// Winsock is supplied by Windows/Wine. Using it avoids MT5's manually managed
// WebRequest/SocketConnect allow-list. The image enables DLL imports explicitly.
#import "ws2_32.dll"
int WSAStartup(ushort version, uchar &data[]);
int WSACleanup();
ulong socket(int family, int kind, int protocol);
int connect(ulong handle, uchar &address[], int length);
int ioctlsocket(ulong handle, int command, uint &argument);
int send(ulong handle, uchar &data[], int length, int flags);
int closesocket(ulong handle);
#import

ulong connection = (ulong)-1;
ulong sequence = 0;
string session;
bool started = false;

string Escape(string value)
{
   StringReplace(value, "\\", "\\\\");
   StringReplace(value, "\"", "\\\"");
   StringReplace(value, "\r", "\\r");
   StringReplace(value, "\n", "\\n");
   StringReplace(value, "\t", "\\t");
   return value;
}

void Disconnect()
{
   if(connection != (ulong)-1) closesocket(connection);
   connection = (ulong)-1;
}

bool ConnectLocal()
{
   Disconnect();
   connection = socket(2, 1, 6); // AF_INET, SOCK_STREAM, IPPROTO_TCP
   if(connection == (ulong)-1) return false;
   uchar address[16];
   ArrayInitialize(address, 0);
   address[0] = 2;
   address[2] = (uchar)(18823 >> 8);
   address[3] = (uchar)(18823 & 255);
   address[4] = 127;
   address[7] = 1; // Only 127.0.0.1; never expose this protocol on the network.
   if(connect(connection, address, 16) != 0)
   {
      Disconnect();
      return false;
   }
   uint nonblocking = 1;
   if(ioctlsocket(connection, (int)0x8004667e, nonblocking) != 0)
   {
      Disconnect();
      return false;
   }
   return true;
}

string Identity(string kind)
{
   return StringFormat("{\"type\":\"%s\",\"account_number\":%I64d,\"server\":\"%s\","
                       "\"symbol\":\"%s\",\"session\":\"%s\",\"seq\":%I64u",
                       kind, AccountInfoInteger(ACCOUNT_LOGIN),
                       Escape(AccountInfoString(ACCOUNT_SERVER)), Escape(_Symbol), session, sequence);
}

void SendFrame(string frame)
{
   if(connection == (ulong)-1) return;
   uchar bytes[];
   int count = StringToCharArray(frame + "\n", bytes, 0, WHOLE_ARRAY, CP_UTF8) - 1;
   // A short write terminates this stream. Never block the EA retrying a quote;
   // the receiver discards an incomplete JSON line when the connection closes.
   if(send(connection, bytes, count, 0) != count) Disconnect();
}

int OnInit()
{
   if(!MQLInfoInteger(MQL_DLLS_ALLOWED)) return INIT_FAILED;
   uchar data[512]; // Larger than WSADATA on both supported Windows ABIs.
   if(WSAStartup(0x0202, data) != 0) return INIT_FAILED;
   started = true;
   session = StringFormat("%I64d-%I64u", ChartID(), GetTickCount64());
   EventSetTimer(1);
   ConnectLocal();
   return INIT_SUCCEEDED;
}

void OnTimer()
{
   if(connection == (ulong)-1) ConnectLocal();
   SendFrame(Identity("heartbeat") + "}");
}

void OnTick()
{
   sequence++;
   MqlTick tick;
   if(!SymbolInfoTick(_Symbol, tick)) return;
   int digits = (int)SymbolInfoInteger(_Symbol, SYMBOL_DIGITS);
   SendFrame(Identity("tick") + StringFormat(
      ",\"time_msc\":%I64d,\"bid\":%s,\"ask\":%s,\"last\":%s,\"flags\":%u}",
      tick.time_msc, DoubleToString(tick.bid, digits), DoubleToString(tick.ask, digits),
      DoubleToString(tick.last, digits), tick.flags));
}

void OnDeinit(const int reason)
{
   EventKillTimer();
   Disconnect();
   if(started) WSACleanup();
}
