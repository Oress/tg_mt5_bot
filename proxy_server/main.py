from flask import Flask, request, jsonify
import os
import mt5service

server_env = os.getenv("MT5_SERVER")
host_env = os.getenv("MT5_HOST")
port_env = os.getenv("MT5_PORT")

def is_none_or_empty(string):
    return string is None or string.strip() == ""

if is_none_or_empty(server_env) or is_none_or_empty(host_env) or is_none_or_empty(port_env):
    raise ValueError("MT5_HOST, MT5_PORT, and MT5_SERVER environment variables must be set")

mt5service = mt5service.MT5Service(server_env, host_env, int(port_env))

app = Flask(__name__)

@app.route('/history-deals', methods=['POST'])
def history_deals():
    data = request.get_json()
    print(data)
    positions = mt5service.history_deals(data['account_number'], data['password'], data['ticket'])
    if hasattr(positions, '__dict__'):
        positions_dict = positions.__dict__
    # If positions is a list/tuple of positions, convert each one
    elif isinstance(positions, (list, tuple)):
        positions_dict = [pos.__dict__ if hasattr(pos, '__dict__') else pos for pos in positions]
    else:
        positions_dict = positions

    return jsonify({"history": positions_dict})

@app.route('/check-credentials', methods=['POST'])
def check_credentials():
    data = request.get_json()
    print(data)
    is_valid = mt5service.check_credentials(data['account_number'], data['password'])
    return jsonify({"valid": is_valid})

@app.route('/account-info', methods=['POST'])
def account_info():
    data = request.get_json()
    print(data)
    balance = mt5service.get_balance(data['account_number'], data['password'])
    return jsonify(balance)

@app.route('/get-price-for-short', methods=['POST'])
def get_price_for_short():
    data = request.get_json()
    print(data)
    price = mt5service.get_info_tick(data['account_number'], data['password'], data['symbol'])
    return jsonify({"price": price.bid})

@app.route('/get-price-tick', methods=['POST'])
def get_price_tick():
    data = request.get_json()
    print(data)
    tick = mt5service.get_info_tick(data['account_number'], data['password'], data['symbol'])
    tick_dict={}
    if hasattr(tick, '__dict__'):
        tick_dict = tick.__dict__
    # If positions is a list/tuple of positions, convert each one
    else:
        tick_dict = tick
    return jsonify({"tick": tick_dict})

@app.route('/positions-get', methods=['POST'])
def positions_get():
    data = request.get_json()
    print(data)
    positions = mt5service.positions_get(data['account_number'], data['password'], data['symbol'])

    # Convert positions to dictionary format if it's a single position
    positions_dict={}
    if hasattr(positions, '__dict__'):
        positions_dict = positions.__dict__
    # If positions is a list/tuple of positions, convert each one
    elif isinstance(positions, (list, tuple)):
        positions_dict = [pos.__dict__ if hasattr(pos, '__dict__') else pos for pos in positions]
    else:
        positions_dict = positions

    return jsonify({"positions": positions_dict})

@app.route('/positions-get-all', methods=['POST'])
def positions_get_all():
    data = request.get_json()
    positions = mt5service.positions_get_all(data['account_number'], data['password'])

    # Convert positions to dictionary format if it's a single position
    positions_dict={}
    if hasattr(positions, '__dict__'):
        positions_dict = positions.__dict__
    # If positions is a list/tuple of positions, convert each one
    elif isinstance(positions, (list, tuple)):
        positions_dict = [pos.__dict__ if hasattr(pos, '__dict__') else pos for pos in positions]
    else:
        positions_dict = positions

    return jsonify({"positions": positions_dict})

@app.route('/place-short-order', methods=['POST'])
def place_short_order():
    data = request.get_json()
    print(data)
    order_result = mt5service.place_short_order(data['account_number'], data['password'], data['position_id'], data['symbol'], data['volume'])
    order_result_dict={}
    if hasattr(order_result, '__dict__'):
        order_result_dict = order_result.__dict__
        order_result_dict['request'] = order_result.request.__dict__
    # If positions is a list/tuple of positions, convert each one
    else:
        order_result_dict = order_result

    return jsonify({"order": order_result_dict})

@app.route('/close-short-order', methods=['POST'])
def close_short_order():
    data = request.get_json()
    print(data)
    order_result = mt5service.close_short_order(data['account_number'], data['password'], data['position_id'], data['symbol'], data['volume'])
    order_result_dict={}
    if hasattr(order_result, '__dict__'):
        order_result_dict = order_result.__dict__
        order_result_dict['request'] = order_result.request.__dict__
    # If positions is a list/tuple of positions, convert each one
    else:
        order_result_dict = order_result
    return jsonify({"order": order_result_dict})

@app.route('/symbol-info', methods=['POST'])
def symbol_info():
    data = request.get_json()
    print(data)
    sym_info = mt5service.symbol_info(data['account_number'], data['password'], data['symbol'])
    return jsonify({"info": sym_info})

# @app.route('/get-balance', methods=['POST'])
# def get_balance():
#     data = request.get_json()
#     print(data)
#     balance = mt5service.get_balance(data['account_number'], data['password'], data['symbol'])
#     return jsonify({"order": balance})

@app.route('/determine-account-type', methods=['POST'])
def determine_account_type():
    data = request.get_json()
    print(data)
    acc_type = mt5service.determine_account_type(data['account_number'], data['password'])
    return jsonify({"acc_type": acc_type})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000)