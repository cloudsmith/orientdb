/*
 * Copyright 1999-2010 Luca Molino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Represents the main entry-point to work with OrientDB databases.
 * 
 * @author Luca Molino
 */
function ODatabase(databasePath) {
	this.databaseUrl = "";
	this.databaseName = "";
	this.databaseInfo = null;
	this.queryResult = null;
	this.commandResult = null;
	this.errorMessage = null;
	this.evalResponse = true;

	if (databasePath) {
		this.databaseUrl = databasePath.substring(0, databasePath
				.lastIndexOf('/'));
		this.databaseName = databasePath.substring(databasePath
				.lastIndexOf('/') + 1);
	}

	this.getDatabaseInfo = function() {
		return this.databaseInfo;
	}
	this.setDatabaseInfo = function(iDatabaseInfo) {
		this.databaseInfo = iDatabaseInfo;
	}

	this.getQueryResult = function() {
		return this.queryResult;
	}
	this.setQueryResult = function(iQueryResult) {
		this.queryResult = iQueryResult;
	}

	this.getCommandResult = function() {
		return this.commandResult;
	}
	this.setCommandResult = function(iCommandResult) {
		this.commandResult = iCommandResult;
	}

	this.getErrorMessage = function() {
		return this.errorMessage;
	}
	this.setErrorMessage = function(iErrorMessage) {
		this.errorMessage = iErrorMessage;
	}

	this.getDatabaseUrl = function() {
		return databaseUrl;
	}
	this.setDatabaseUrl = function(iDatabaseUrl) {
		this.databaseUrl = iDatabaseUrl;
	}

	this.getDatabaseName = function() {
		return this.databaseName;
	}
	this.setDatabaseName = function(iDatabaseName) {
		this.databaseName = iDatabaseName;
	}

	this.getEvalResponse = function() {
		return this.evalResponse;
	}
	this.setEvalResponse = function(iEvalResponse) {
		this.evalResponse = iEvalResponse;
	}

	this.open = function() {
		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/connect/' + this.databaseName,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setDatabaseInfo(eval("(" + msg + ")"));
				} else {
					this.setDatabaseInfo(msg);
				}
			},
			error : function(msg) {
				this.setErrorMessage('Connect error: ' + msg.responseText);
				this.setDatabaseInfo(null);
			}
		});
		return this.getDatabaseInfo();
	}

	this.open = function(userName, userPass) {
		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/connect/' + this.databaseName,
			context : this,
			async : false,
			username : userName,
			password : userPass,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setDatabaseInfo(eval("(" + msg + ")"));
				} else {
					this.setDatabaseInfo(msg);
				}
			},
			error : function(msg) {
				this.setErrorMessage('Connect error: ' + msg.responseText);
				this.setDatabaseInfo(null);
			}
		});
		return this.getDatabaseInfo();
	}

	this.query = function(iQuery) {
		if (this.databaseInfo == null) {
			this.open();
		}
		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/query/' + this.databaseName + '/sql/'
					+ iQuery,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setQueryResult(eval("(" + msg + ")"));
				} else {
					this.setQueryResult(msg);
				}
			},
			error : function(msg) {
				this.setQueryResult(null);
				this.setErrorMessage('Query error: ' + msg.responseText);
			}
		});
		return this.getQueryResult();
	}

	this.query = function(iQuery, iLimit, iFetchPlan) {
		if (this.databaseInfo == null) {
			this.open();
		}
		if (iLimit == null || iLimit == '') {
			iLimit = '';
		} else {
			iLimit = '/' + iLimit;
		}
		if (iFetchPlan == null || iFetchPlan == '') {
			iFetchPlan = '';
		} else {
			if (iLimit == '') {
				iLimit = '/20';
			}
			iFetchPlan = '/' + iFetchPlan;
		}
		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/query/' + this.databaseName + '/sql/'
					+ iQuery + iLimit + iFetchPlan,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setQueryResult(eval("(" + msg + ")"));
				} else {
					this.setQueryResult(msg);
				}
			},
			error : function(msg) {
				this.setQueryResult(null);
				this.setErrorMessage('Query error: ' + msg.responseText);
			}
		});
		return this.getQueryResult();
	}

	this.load = function(iRID) {
		if (this.databaseInfo == null) {
			this.open();
		}

		if (iRID.charAt(0) == '#')
			iRID = iRID.substring(1);

		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/document/' + this.databaseName + '/'
					+ iRID,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setQueryResult(eval("(" + msg + ")"));
				} else {
					this.setQueryResult(msg);
				}
			},
			error : function(msg) {
				this.setQueryResult(null);
				this.setErrorMessage('Query error: ' + msg.responseText);
			}
		});
		return this.getQueryResult();
	}

	this.load = function(iRID, iFetchPlan) {
		if (this.databaseInfo == null) {
			this.open();
		}

		if (iRID.charAt(0) == '#')
			iRID = iRID.substring(1);

		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/document/' + this.databaseName + '/'
					+ iRID + '/' + iFetchPlan,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setQueryResult(eval("(" + msg + ")"));
				} else {
					this.setQueryResult(msg);
				}
			},
			error : function(msg) {
				this.setQueryResult(null);
				this.setErrorMessage('Query error: ' + msg.responseText);
			}
		});
		return this.getQueryResult();
	}

	this.classInfo = function(iClassName) {
		if (this.databaseInfo == null) {
			this.open();
		}
		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/class/' + this.databaseName + '/'
					+ iClassName,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setCommandResult(eval("(" + msg + ")"));
				} else {
					this.setCommandResult(msg);
				}
			},
			error : function(msg) {
				this.setCommandResult(null);
				this.setErrorMessage('Command error: ' + msg.responseText);
			}
		});
		return this.getCommandResult();
	}

	this.createClass = function(iClassName) {
		if (this.databaseInfo == null) {
			this.open();
		}
		$.ajax({
			type : "POST",
			url : this.databaseUrl + '/class/' + this.databaseName + '/'
					+ iClassName,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				this.setCommandResult(msg);
			},
			error : function(msg) {
				this.setCommandResult(null);
				this.setErrorMessage('Command error: ' + msg.responseText);
			}
		});
		return this.getCommandResult();
	}

	this.browseCluster = function(iClassName) {
		if (this.databaseInfo == null) {
			this.open();
		}
		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/cluster/' + this.databaseName + '/'
					+ iClassName,
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setCommandResult(eval("(" + msg + ")"));
				} else {
					this.setCommandResult(msg);
				}
			},
			error : function(msg) {
				this.setCommandResult(null);
				this.setErrorMessage('Command error: ' + msg.responseText);
			}
		});
		return this.getCommandResult();
	}

	this.executeCommand = function(iCommand) {
		if (this.databaseInfo == null) {
			this.open();
		}
		$.ajax({
			type : "POST",
			url : this.databaseUrl + '/command/' + this.databaseName + '/sql/'
					+ iCommand + "/",
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				this.setCommandResult(msg);
			},
			error : function(msg) {
				this.setCommandResult(null);
				this.setErrorMessage('Command error: ' + msg.responseText);
			}
		});
		return this.getCommandResult();
	}

	this.serverInfo = function() {
		if (this.databaseInfo == null) {
			this.open();
		}
		$.ajax({
			type : "GET",
			url : this.databaseUrl + '/server',
			context : this,
			async : false,
			success : function(msg) {
				this.setErrorMessage(null);
				if (this.getEvalResponse()) {
					this.setCommandResult(eval("(" + msg + ")"));
				} else {
					this.setCommandResult(msg);
				}
			},
			error : function(msg) {
				this.setCommandResult(null);
				this.setErrorMessage('Command error: ' + msg.responseText);
			}
		});
		return this.getCommandResult();
	}

	this.schema = function() {
		if (this.databaseInfo == null) {
			this.setErrorMessage('Database is closed');
			return null;
		}
		if (this.getEvalResponse()) {
			return this.getDatabaseInfo()['classes'];
		} else {
			return eval('(' + this.getDatabaseInfo() + ')')['classes'];
		}
	}

	this.securityRoles = function() {
		if (this.databaseInfo == null) {
			this.setErrorMessage('Database is closed');
			return null;
		}
		if (this.getEvalResponse()) {
			return this.getDatabaseInfo()['roles'];
		} else {
			return eval('(' + this.getDatabaseInfo() + ')')['roles'];
		}
	}

	this.securityUsers = function() {
		if (this.databaseInfo == null) {
			this.setErrorMessage('Database is closed');
			return null;
		}
		if (this.getEvalResponse()) {
			return this.getDatabaseInfo()['users'];
		} else {
			return eval('(' + this.getDatabaseInfo() + ')')['users'];
		}
	}

	this.close = function() {
		if (this.databaseInfo != null) {
			$.ajax({
				type : 'GET',
				url : this.databaseUrl + '/disconnect',
				dataType : "json",
				async : false,
				context : this,
				success : function(msg) {
					this.setCommandResult(msg);
					this.setErrorMessage(null);
				},
				error : function(msg) {
					this.setCommandResult(null);
					this.setErrorMessage('Command response: '
							+ msg.responseText);
				}
			});
		}
		this.databaseInfo = null;
		return this.getCommandResult();
	}
}
