// Our application
var app = angular.module('podcache',[ 'ngResource' ]);

// Create a REST-service for the application.
app.factory('Feed', ['$resource',
    function($resource) {
	    return $resource('feed/:name', {}, {
	    	'update': { method:'PUT', isArray: true }
	    });
  	}
]);

// Controller for listing all available feeds.
app.controller('feedlist', function(Feed, $scope) {
	// Query available feeds
	$scope.feeds = Feed.query();
	$scope.selectedFeed = null;
	$scope.addNew = false;
    
	$scope.showAddForm = function() {
		$scope.selectedFeed = null;
		$scope.addNew = true;
	}
	
    $scope.showEditForm = function(feed) {
    	$scope.selectedFeed = feed;
    	$scope.addNew = false;
    }
});

// Sub-Controller for adding additional feeds.
app.controller('newFeed', function($scope, Feed) {
	$scope.failure = false;
	$scope.success = false;
	
	// Function to add a feed.
	$scope.addFeed = function() {
		$scope.failure = false;
		$scope.success = false;
		
		var newFeed = new Feed({name: $scope.name, url: $scope.url });
		
		newFeed.$save(function(addedFeed) {
			// Add to the list of feeds.
			$scope.$parent.feeds.push(addedFeed);
			
			// Clear fields
			$scope.name = '';
			$scope.url = '';
			
			// Mark for success.
			$scope.success = true;
		}, function(error) {
			// Mark failure.
			$scope.failure = true;
			$scope.success = false;
		});
    }
});

//Sub-Controller for editing an existing feed.
app.controller('editFeed', function($scope, Feed) {
	$scope.success = false;
	$scope.failure = false;
	
	$scope.$parent.$watch('selectedFeed',function(value) {
		//$scope.name = value.name;
		$scope.url = value.url;
		$scope.contentType = value.contentType;
		$scope.markedForDeletion = value.markedForDeletion;
		$scope.success = false;
		$scope.failure = false;
	});

	$scope.update = function() {
		var selectedFeed = $scope.$parent.selectedFeed;
		
		var updatedFeed = angular.copy(selectedFeed);
		updatedFeed.url = $scope.url;
		updatedFeed.contentType = $scope.contentType;
		updatedFeed.markedForDeletion = $scope.markedForDeletion;

		$scope.failure = false;
		$scope.success = false;
		
		Feed.update({ name: updatedFeed.name }, updatedFeed, function() {
			selectedFeed.url = updatedFeed.url;
			selectedFeed.contentType = updatedFeed.contentType;
			selectedFeed.markedForDeletion = updatedFeed.markedForDeletion;
			
			$scope.success = true;
		}, function() {
			$scope.success = false;
			$scope.failure = true;
		});
	}
});
